package org.erlide.engine.services.search;

import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.IPath;
import org.erlide.engine.model.ErlModelException;
import org.erlide.engine.model.IErlElement;
import org.erlide.engine.model.root.IErlElementLocator;
import org.erlide.engine.model.root.IErlModel;
import org.erlide.engine.model.root.IErlModule;
import org.erlide.engine.model.root.IErlProject;

import com.ericsson.otp.erlang.OtpErlangObject;

public interface ModelUtilService {

    Object getTarget(final IContainer container, final IPath path,
            final boolean checkResourceExistence);

    IErlProject getProject(final IErlElement element);

    IErlModule getModule(final IErlElement element);

    boolean isOtpModule(final IErlModule module);

    String[] getPredefinedMacroNames();

    List<OtpErlangObject> getImportsAsList(final IErlModule mod);

    List<String> findUnitsWithPrefix(final String prefix, final IErlProject project,
            final boolean checkExternals, final boolean includes)
            throws ErlModelException;

    IErlModule getModuleFromExternalModulePath(final IErlModel model,
            final String modulePath) throws ErlModelException;

    String getExternalModulePath(final IErlElementLocator model, final IErlModule module);

    String getModuleInfo(IErlModule module);

}
