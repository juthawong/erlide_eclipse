/*******************************************************************************
 * Copyright (c) 2008 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution.
 * 
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.erlide.runtime.PreferencesUtils;
import org.osgi.service.prefs.BackingStoreException;

public final class LibraryLocation extends DependencyLocation {
	private static final String SOURCES = "sources";
	private static final String INCLUDES = "includes";
	private static final String OUTPUT = "output";

	private List<SourceLocation> sources = new ArrayList<SourceLocation>();
	private List<String> includes = new ArrayList<String>();
	private String output;
	private List<LibraryLocation> libraries = new ArrayList<LibraryLocation>();

	public LibraryLocation(List<SourceLocation> sources, List<String> includes,
			String output, List<LibraryLocation> libraries) {
		if (sources != null) {
			this.sources = sources;
		}
		if (includes != null) {
			this.includes = includes;
		}
		this.output = output;
		if (libraries != null) {
			this.libraries = libraries;
		}
	}

	public Collection<SourceLocation> getSources() {
		return Collections.unmodifiableCollection(sources);
	}

	public Collection<String> getIncludes() {
		return Collections.unmodifiableCollection(includes);
	}

	public String getOutput() {
		return output;
	}

	public Collection<LibraryLocation> getLibraries() {
		return Collections.unmodifiableCollection(libraries);
	}

	@Override
	public void load(IEclipsePreferences root) {

	}

	@Override
	public void store(IEclipsePreferences root) throws BackingStoreException {
		PreferencesUtils.clearAll(root);
		root.put(OUTPUT, output);
		IEclipsePreferences node = (IEclipsePreferences) root.node(SOURCES);
		for (SourceLocation loc : sources) {
			loc.store((IEclipsePreferences) node.node(loc.getDirectory()));
		}
		root.put(INCLUDES, PreferencesUtils.packList(includes));
		root.flush();
	}

}
