package erlang;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.erlide.core.erlang.util.Util;
import org.erlide.jinterface.rpc.RpcException;
import org.erlide.runtime.backend.Backend;
import org.erlide.runtime.backend.exceptions.BackendException;

import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;

public class ErlideContextAssist {

	public static Collection<String> getVariables(final Backend b,
			final String src, final String prefix) {
		final SortedSet<String> result = new TreeSet<String>();
		try {
			final OtpErlangObject res = b.rpcx("erlide_content_assist",
					"get_variables", "ss", src, prefix);
			if (Util.isOk(res)) {
				final OtpErlangTuple t = (OtpErlangTuple) res;
				final OtpErlangList l = (OtpErlangList) t.elementAt(1);
				for (final OtpErlangObject i : l.elements()) {
					result.add(Util.stringValue(i));
				}
			}
		} catch (final RpcException e) {
			e.printStackTrace();
		} catch (final BackendException e) {
			e.printStackTrace();
		}
		return result;
	}

}