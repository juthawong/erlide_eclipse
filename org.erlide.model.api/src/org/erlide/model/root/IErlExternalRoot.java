/**
 *
 */
package org.erlide.model.root;

import java.util.List;

/**
 * @author jakob
 * 
 */
public interface IErlExternalRoot extends IErlExternal {

    List<IErlElement> internalGetChildren();

    void removeExternal();

}