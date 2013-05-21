/*******************************************************************************
 * Copyright (c) 2010 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.backend.internal;

import java.io.IOException;
import java.net.Socket;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IProcess;
import org.erlide.backend.api.BackendException;
import org.erlide.runtime.api.ErlSystemStatus;
import org.erlide.runtime.api.IErlRuntime;
import org.erlide.runtime.api.IRpcSite;
import org.erlide.runtime.api.IRuntimeStateListener;
import org.erlide.runtime.api.RuntimeData;
import org.erlide.runtime.rpc.RpcException;
import org.erlide.runtime.rpc.RpcSite;
import org.erlide.runtime.shell.IBackendShell;
import org.erlide.util.ErlLogger;
import org.erlide.util.HostnameUtils;
import org.erlide.util.IProvider;
import org.erlide.util.MessageReporter;
import org.erlide.util.SystemConfiguration;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.ericsson.otp.erlang.OtpMbox;
import com.ericsson.otp.erlang.OtpNode;
import com.ericsson.otp.erlang.OtpNodeStatus;
import com.google.common.base.Strings;

public class ErlRuntime implements IErlRuntime {
    private static final String COULD_NOT_CONNECT_TO_BACKEND = "Could not connect to backend! Please check runtime settings.";
    private static final int EPMD_PORT = 4369;

    private static final int MAX_RETRIES = 15;
    public static final int RETRY_DELAY = Integer.parseInt(System.getProperty(
            "erlide.connect.delay", "400"));
    private static final Object connectLock = new Object();

    public enum State {
        CONNECTED, DISCONNECTED, DOWN
    }

    private State state;
    private final RuntimeData data;
    private OtpNode localNode;
    private final Object localNodeLock = new Object();
    private boolean reported;
    private IProcess process;
    private final boolean connectOnce;
    private final IProvider<IProcess> processProvider;
    private final OtpNodeStatus statusWatcher;
    private OtpMbox eventBox;
    private boolean stopped;
    private IRuntimeStateListener listener;
    private ErlSystemStatus lastSystemMessage;
    private final IRpcSite rpcSite;

    public ErlRuntime(final RuntimeData data,
            final IProvider<IProcess> processProvider) {
        this.data = data;
        this.processProvider = processProvider;
        connectOnce = data.isInternal();
        statusWatcher = new OtpNodeStatus() {
            @Override
            public void remoteStatus(final String node, final boolean up,
                    final Object info) {
                if (node.equals(getNodeName())) {
                    if (up) {
                        ErlLogger.debug("Node %s is up", getNodeName());
                        connectRetry();
                    } else {
                        ErlLogger.debug("Node %s is down: %s", getNodeName(),
                                info);
                        state = State.DOWN;
                    }
                }
            }
        };
        start();
        rpcSite = new RpcSite(this, localNode, getNodeName());
    }

    @Override
    public void start() {
        process = processProvider.get();
        state = State.DISCONNECTED;
        stopped = false;

        startLocalNode();
        // if (epmdWatcher.isRunningNode(name)) {
        // connect();
        // }
    }

    public void startLocalNode() {
        boolean nodeCreated = false;
        synchronized (localNodeLock) {
            int i = 0;
            do {
                try {
                    i++;
                    localNode = ErlRuntime.createOtpNode(data.getCookie(),
                            data.hasLongName());
                    localNode.registerStatusHandler(statusWatcher);
                    nodeCreated = true;
                } catch (final IOException e) {
                    ErlLogger
                            .error("ErlRuntime could not be created (%s), retrying %d",
                                    e.getMessage(), i);
                    try {
                        localNodeLock.wait(300);
                    } catch (final InterruptedException e1) {
                    }
                }
            } while (!nodeCreated && i < 10);

        }
    }

    @Override
    public String getNodeName() {
        return data.getQualifiedNodeName();
    }

    private boolean connectRetry() {
        int tries = MAX_RETRIES;
        boolean ok = false;
        while (!ok && tries > 0) {
            ErlLogger.debug("# ping..." + getNodeName() + " "
                    + Thread.currentThread().getName());
            ok = localNode.ping(getNodeName(), RETRY_DELAY
                    + (MAX_RETRIES - tries) * RETRY_DELAY % 3);
            tries--;
        }
        return ok;
    }

    @Override
    public void tryConnect() throws RpcException {
        synchronized (connectLock) {
            switch (state) {
            case DISCONNECTED:
                reported = false;
                if (connectRetry()) {
                    state = State.CONNECTED;
                } else if (connectOnce) {
                    state = State.DOWN;
                } else {
                    state = State.DISCONNECTED;
                }
                break;
            case CONNECTED:
                break;
            case DOWN:
                if (listener != null) {
                    listener.runtimeDown(this);
                }
                try {
                    if (process != null) {
                        process.terminate();
                        process = null;
                    }
                } catch (final DebugException e) {
                    ErlLogger.info(e);
                }
                if (!stopped) {
                    final String msg = reportRuntimeDown(getNodeName());
                    throw new RpcException(msg);
                }
            }
        }
    }

    private String reportRuntimeDown(final String peer) {
        final String fmt = "Backend '%s' is down";
        final String msg = String.format(fmt, peer);
        // TODO when to report errors?
        final boolean shouldReport = data.isInternal() || data.isReportErrors();
        if (shouldReport && !reported) {
            final String user = System.getProperty("user.name");

            String msg1;
            if (connectOnce) {
                msg1 = "It is likely that your network is misconfigured or uses 'strange' host names.\n\n"
                        + "Please check the "
                        + "Window->preferences->erlang->network page for hints about that. \n\n"
                        + "Also, check if you can create and connect two erlang nodes on your machine "
                        + "using \"erl -name foo1\" and \"erl -name foo2\".";
            } else {
                msg1 = "If you didn't shut it down on purpose, it is an "
                        + "unrecoverable error, please restart Eclipse. ";
            }

            final String details = "If an error report named '"
                    + user
                    + "_<timestamp>.txt' has been created in your home directory, "
                    + "please consider reporting the problem. \n"
                    + (SystemConfiguration
                            .hasFeatureEnabled("erlide.ericsson.user") ? ""
                            : "http://www.assembla.com/spaces/erlide/support/tickets");
            MessageReporter.showError(msg, msg1 + "\n\n" + details);
            reported = true;
        }

        final ErlSystemStatus status = getSystemStatus();
        ErlLogger.error("Last system status was:\n %s",
                status != null ? status.prettyPrint() : "null");

        return msg;
    }

    @Override
    public boolean isAvailable() {
        return state == State.CONNECTED;
    }

    public static String createJavaNodeName() {
        final String fUniqueId = ErlRuntime.getTimeSuffix();
        return "jerlide_" + fUniqueId;
    }

    public static String createJavaNodeName(final String hostName) {
        return createJavaNodeName() + "@" + hostName;
    }

    static String getTimeSuffix() {
        String fUniqueId;
        fUniqueId = Long.toHexString(System.currentTimeMillis() & 0xFFFFFFF);
        return fUniqueId;
    }

    public static OtpNode createOtpNode(final String cookie,
            final boolean longName) throws IOException {
        OtpNode node;
        final String hostName = HostnameUtils.getErlangHostName(longName);
        if (Strings.isNullOrEmpty(cookie)) {
            node = new OtpNode(createJavaNodeName(hostName));
        } else {
            node = new OtpNode(createJavaNodeName(hostName), cookie);
        }
        debugPrintCookie(node.cookie());
        return node;
    }

    private static void debugPrintCookie(final String cookie) {
        final int len = cookie.length();
        final String trimmed = len > 7 ? cookie.substring(0, 7) : cookie;
        ErlLogger.debug("using cookie '%s...'%d (info: '%s')", trimmed, len,
                cookie);
    }

    @Override
    public OtpMbox createMbox(final String name) {
        return localNode.createMbox(name);
    }

    @Override
    public OtpMbox createMbox() {
        return localNode.createMbox();
    }

    @Override
    public void stop() {
        // close peer too?
        if (stopped) {
            return;
        }
        stopped = true;
        localNode.close();
    }

    @Override
    public void connect() {
        final String label = getNodeName();
        ErlLogger.debug(label + ": waiting connection to peer...");
        try {
            wait_for_epmd();
            eventBox = createMbox("rex");

            if (waitForCodeServer()) {
                ErlLogger.debug("connected!");
            } else {
                ErlLogger.error(COULD_NOT_CONNECT_TO_BACKEND);
            }

        } catch (final BackendException e) {
            ErlLogger.error(e);
            ErlLogger.error(COULD_NOT_CONNECT_TO_BACKEND);
        } catch (final Exception e) {
            ErlLogger.error(e);
            ErlLogger.error(COULD_NOT_CONNECT_TO_BACKEND);
        }
    }

    private OtpMbox getEventBox() {
        return eventBox;
    }

    @Override
    public OtpErlangPid getEventPid() {
        final OtpMbox theEventBox = getEventBox();
        if (theEventBox == null) {
            return null;
        }
        return theEventBox.self();
    }

    private void wait_for_epmd() throws BackendException {
        wait_for_epmd("localhost");
    }

    private void wait_for_epmd(final String host) throws BackendException {
        // If anyone has a better solution for waiting for epmd to be up, please
        // let me know
        int tries = 50;
        boolean ok = false;
        do {
            Socket s;
            try {
                s = new Socket(host, EPMD_PORT);
                s.close();
                ok = true;
            } catch (final IOException e) {
            }
            try {
                Thread.sleep(100);
                // ErlLogger.debug("sleep............");
            } catch (final InterruptedException e1) {
            }
            tries--;
        } while (!ok && tries > 0);
        if (!ok) {
            final String msg = "Couldn't contact epmd - erlang backend is probably not working\n"
                    + "  Possibly your host's entry in /etc/hosts is wrong ("
                    + host + ").";
            ErlLogger.error(msg);
            throw new BackendException(msg);
        }
    }

    private boolean waitForCodeServer() {
        try {
            OtpErlangObject r;
            int i = 30;
            boolean gotIt = false;
            do {
                r = rpcSite.call("erlang", "whereis", "a", "code_server");
                gotIt = !(r instanceof OtpErlangPid);
                if (!gotIt) {
                    try {
                        Thread.sleep(200);
                    } catch (final InterruptedException e) {
                    }
                }
                i--;
            } while (gotIt && i > 0);
            if (gotIt) {
                ErlLogger.error("code server did not start in time for %s",
                        getNodeName());
                return false;
            }
            ErlLogger.debug("code server started");
            return true;
        } catch (final Exception e) {
            ErlLogger.error("error starting code server for %s: %s",
                    getNodeName(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isStopped() {
        return stopped || !isAvailable();
    }

    @Override
    public OtpMbox getEventMbox() {
        return eventBox;
    }

    @Override
    public RuntimeData getRuntimeData() {
        return data;
    }

    @Override
    public void restart() {
        if (!data.isRestartable()) {
            return;
        }
        if (!stopped) {
            stop();
        }
        System.out.println("RESTART " + this);
        start();
        // state = State.DISCONNECTED;
        // stopped = false;
        // process = processProvider.get();
    }

    @Override
    public void addListener(final IRuntimeStateListener aListener) {
        listener = aListener;
    }

    @Override
    public IBackendShell getShell(final String id) {
        // TODO can we return something here?
        return null;
    }

    @Override
    public ErlSystemStatus getSystemStatus() {
        return lastSystemMessage;
    }

    @Override
    public void setSystemStatus(final ErlSystemStatus msg) {
        // System.out.println(msg.prettyPrint());
        lastSystemMessage = msg;
    }

    @Override
    public void dispose() {
        stop();
        process = null;
        listener = null;
    }

    @Override
    public IRpcSite getRpcSite() {
        return rpcSite;
    }
}