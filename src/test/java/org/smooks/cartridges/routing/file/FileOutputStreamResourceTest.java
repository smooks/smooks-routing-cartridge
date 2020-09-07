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

import org.smooks.FilterSettings;
import org.smooks.Smooks;
import org.smooks.cartridges.javabean.Bean;
import org.smooks.cartridges.templating.OutputTo;
import org.smooks.cartridges.templating.TemplatingConfiguration;
import org.smooks.cartridges.templating.freemarker.FreeMarkerTemplateProcessor;
import org.smooks.cdr.SmooksResourceConfiguration;
import org.smooks.cdr.injector.Scope;
import org.smooks.cdr.lifecycle.LifecycleManager;
import org.smooks.cdr.lifecycle.phase.PostConstructLifecyclePhase;
import org.smooks.cdr.registry.Registry;
import org.smooks.cdr.registry.lookup.LifecycleManagerLookup;
import org.smooks.container.ExecutionContext;
import org.smooks.container.MockApplicationContext;
import org.smooks.container.MockExecutionContext;
import org.smooks.delivery.Fragment;
import org.smooks.io.AbstractOutputStreamResource;
import org.smooks.io.FileUtils;
import org.smooks.payload.StringSource;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import static org.testng.AssertJUnit.*;

/**
 * Unit test for {@link FileOutputStreamResource}
 * 
 * @author <a href="mailto:daniel.bevenius@gmail.com">Daniel Bevenius</a>
 */
@Test ( groups = "unit" )
public class FileOutputStreamResourceTest 
{
	private String resourceName = "testResourceName";
	private String fileNamePattern = "testFileName";
	private String destinationDirectory = System.getProperty( "java.io.tmpdir" );
	private String listFileName = "testListFileName";
	private FileOutputStreamResource resource = new FileOutputStreamResource();
	private SmooksResourceConfiguration config;
    private File file1 = new File("target/config-01-test/1/1.xml");
    private File file2 = new File("target/config-01-test/2/2.xml");
    private File file3 = new File("target/config-01-test/3/3.xml");
	
    @BeforeClass
    public void setUp() throws Exception {
        Registry registry = new MockApplicationContext().getRegistry();
        LifecycleManager lifecycleManager = registry.lookup(new LifecycleManagerLookup());
        config = createConfig( resourceName, fileNamePattern, destinationDirectory, listFileName);
        lifecycleManager.applyPhase(resource, new PostConstructLifecyclePhase(new Scope(registry, config, resource)));
        deleteFiles();
    }

    @Test
	public void configure()
	{
        assertEquals(resourceName, resource.getResourceName() );
	}
	
	@Test
	public void visit() throws Exception
	{
		MockExecutionContext executionContext = new MockExecutionContext();
		resource.visitBefore((Element)null, executionContext );
		
		OutputStream outputStream = AbstractOutputStreamResource.getOutputStream(resource.getResourceName(), executionContext );
		assertTrue(outputStream instanceof FileOutputStream );
		
		resource.executeVisitLifecycleCleanup(new Fragment((Element)null), executionContext);
		
		assertThatFilesWereGenerated(executionContext);
	}
	
	private void assertThatFilesWereGenerated(ExecutionContext executionContext) throws Exception
	{
	    File file = new File ( destinationDirectory, fileNamePattern );
        assertTrue( file.exists() );
        
        List<String> listFileNames = FileListAccessor.getListFileNames( executionContext );
        assertNotNull( listFileNames );
        assertTrue( listFileNames.size() == 1 );
        
        for (String listFile : listFileNames)
        {
            List<String> fileList = FileListAccessor.getFileList( executionContext, listFile );
            assertTrue( fileList.size() == 1 );
            for (String fileName : fileList)
            {
                File file2 = new File( fileName );
                assertEquals( fileNamePattern, file2.getName() );
                file2.delete();
            }
            new File( listFile ).delete();
        }
	    
	}
    
    @Test
    public void testConfig01() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("config-01.xml"));

        try {
            smooks.filterSource(new StringSource("<root><a>1</a><a>2</a><a>3</a></root>"));

            assertEquals("1", getFileContents(file1));
            assertEquals("2", getFileContents(file2));
            assertEquals("3", getFileContents(file3));
        } finally {
            smooks.close();
        }
    }

    @Test
    public void config01Programmatic() throws IOException, SAXException {
        Smooks smooks = new Smooks();

        try {
            smooks.setFilterSettings(FilterSettings.DEFAULT_SAX);

            smooks.addVisitors(new Bean(HashMap.class, "object").bindTo("a", "a"));
            smooks.addVisitor(new FreeMarkerTemplateProcessor(new TemplatingConfiguration("${object.a}").setUsage(OutputTo.stream("fileOS"))), "a");
            smooks.addVisitor(new FileOutputStreamResource().setFileNamePattern("${object.a}.xml").setDestinationDirectoryPattern("target/config-01-test/${object.a}").setResourceName("fileOS"), "a");

            smooks.filterSource(new StringSource("<root><a>1</a><a>2</a><a>3</a></root>"));

            assertEquals("1", getFileContents(file1));
            assertEquals("2", getFileContents(file2));
            assertEquals("3", getFileContents(file3));
        } finally {
            smooks.close();
        }
    }
    
    @Test
    public void testAppendingToOutputFile() throws Exception {
        final Smooks smooks = new Smooks();
        final String outputFileName = "appended.txt";
        final String outputStreamRef = "fileOS";
        final File destinationDir = new File("target/config-01-test");
	    final File outputFile = new File(destinationDir, outputFileName);

        try {
            smooks.setFilterSettings(FilterSettings.DEFAULT_SAX);
            smooks.addVisitors(new Bean(HashMap.class, "object").bindTo("a", "a"));
            smooks.addVisitor(new FreeMarkerTemplateProcessor(new TemplatingConfiguration("${object.a}")
                    .setUsage(OutputTo.stream(outputStreamRef))), "a");
            smooks.addVisitor(new FileOutputStreamResource()
                    .setAppend(true)
                    .setFileNamePattern(outputFileName)
                    .setDestinationDirectoryPattern(destinationDir.getAbsolutePath())
                    .setResourceName(outputStreamRef)
                    , "a");

            smooks.filterSource(new StringSource("<root><a>1</a><a>2</a><a>3</a></root>"));

            assertEquals("123", getFileContents(outputFile));
        } finally {
            smooks.close();
            outputFile.delete();
        }
    }

    private String getFileContents(File file) throws IOException {
        return new String(FileUtils.readFile(file));
    }

    @AfterClass
    public void tearDown() throws Exception {
        deleteFiles();
    }

    public void deleteFiles() {
        file1.delete();
        file2.delete();
        file3.delete();
    }

	private SmooksResourceConfiguration createConfig( 
			final String resourceName, 
			final String fileName ,
			final String destinationDirectory ,
			final String listFileName )
	{
    	SmooksResourceConfiguration config = new SmooksResourceConfiguration( "x", FileOutputStreamResource.class.getName() );
		config.setParameter( "resourceName", resourceName );
		config.setParameter( "fileNamePattern", fileName );
		config.setParameter( "destinationDirectoryPattern", destinationDirectory );
		config.setParameter( "listFileNamePattern", listFileName );
		return config;
	}
	
}
	
