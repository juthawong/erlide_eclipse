package org.erlide.backend.internal

import com.ericsson.otp.erlang.OtpErlangTuple
import org.erlide.backend.BackendCore
import org.erlide.backend.events.ErlangEventHandler
import org.erlide.runtime.api.ErlSystemStatus
import org.osgi.service.event.Event

class SystemMonitorHandler extends ErlangEventHandler {

  new(String backendName) {
    super("system_status", backendName);
  }

  override void handleEvent(Event event) {
    val OtpErlangTuple t = event.getProperty("DATA") as OtpErlangTuple

    // TODO publish as osgi event instead
    val b = BackendCore::backendManager.allBackends.findFirst[name==backendName]
    b.systemStatus = new ErlSystemStatus(t)
  }

}
