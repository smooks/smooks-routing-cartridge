/*-
 * ========================LICENSE_START=================================
 * smooks-routing-cartridge
 * %%
 * Copyright (C) 2020 Smooks
 * %%
 * Licensed under the terms of the Apache License Version 2.0, or
 * the GNU Lesser General Public License version 3.0 or later.
 * 
 * SPDX-License-Identifier: Apache-2.0 OR LGPL-3.0-or-later
 * 
 * ======================================================================
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * ======================================================================
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * =========================LICENSE_END==================================
 */
package org.smooks.cartridges.routing.file;

import org.smooks.api.ExecutionContext;
import org.smooks.api.TypedKey;
import org.smooks.assertion.AssertArgument;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FileListAccessor is a utility class that retrieves list file names
 * from the Smooks {@link ExecutionContext}.
 * <p/>
 * 
 * @author <a href="mailto:daniel.bevenius@gmail.com">Daniel Bevenius</a>			
 *
 */
public class FileListAccessor
{
    /*
	 * 	Keys for the entry containing the file lists (used in ExecutionContexts attribute map )
     */
    private static final TypedKey<List<String>> ALL_LIST_FILE_NAME_CONTEXT_KEY = new TypedKey<>();
    
	private FileListAccessor() { }
	
	/**
	 * 	Adds the passes in <code>listFileName</code> to the ExecutionContext. 
	 * 	<p/>
	 *  Note that the filename should be specified with a path. This is so that the same filename can be used 
	 *  in multiple directories.
	 * 
	 * @param fileName 	- list file name to add to the context
	 * @param execContext	- Smooks ExceutionContext
	 */
	public static void addFileName(final String fileName, final ExecutionContext execContext) {
		AssertArgument.isNotNullAndNotEmpty(fileName, "fileName");

		@SuppressWarnings("unchecked")
		List<String> allListFiles = execContext.get(ALL_LIST_FILE_NAME_CONTEXT_KEY);
		if (allListFiles == null) {
			allListFiles = new ArrayList<>();
		}

		//	no need to have duplicates
		if (!allListFiles.contains(fileName)) {
			allListFiles.add(fileName);
		}

		execContext.put(ALL_LIST_FILE_NAME_CONTEXT_KEY, allListFiles);
	}
	
	/**
	 * 	Return the list of files contained in the passed in file "fromFile"
	 * 
	 * @param executionContext	- Smooks execution context
	 * @param fromFile			- path to list file 
	 * @return List<String>		- where String is the absolute path to a file.
	 * @throws IOException		- If the "fromFile" cannot be found or something else IO related goes wrong.
	 */
	public static List<String> getFileList(final ExecutionContext executionContext, String fromFile) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(fromFile));
			List<String> files = new ArrayList<String>();
			String line = null;
			while ((line = reader.readLine()) != null) {
				files.add(line);
			}
			return files;
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static List<String> getListFileNames(final ExecutionContext executionContext) {
		return executionContext.get(ALL_LIST_FILE_NAME_CONTEXT_KEY);
	}

	@SuppressWarnings("unchecked")
	public static List<String> getListFileNames(final Map attributes) {
		return (List<String>) attributes.get(ALL_LIST_FILE_NAME_CONTEXT_KEY);
	}

}
