//
// JODConverter - Java OpenDocument Converter
// Copyright (C) 2004-2007 - Mirko Nasato <mirko@artofsolving.com>
//
// Modified by Rasan Rasch <rasan@nyu.edu>
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
// http://www.gnu.org/copyleft/lesser.html

package edu.nyu.dlib.ptv.convertdoc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.ConnectException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FilenameUtils;

import com.artofsolving.jodconverter.DocumentConverter;
import com.artofsolving.jodconverter.XmlDocumentFormatRegistry;
import com.artofsolving.jodconverter.openoffice.connection.OpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.connection.SocketOpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.converter.OpenOfficeDocumentConverter;

/**
 * Command line tool to convert documents into a different format.
 * <p>
 * Usage: can convert all files in a directory
 * 
 * <pre>
 * ConvertDocument -r document-format.xml -f pdf /input/directory /output/directory
 * </pre>
 */
public class ConvertDocument {

    private static String DEFAULT_FORMAT_REGISTRY = "conf/document-formats.xml";
    private static String DEFAULT_OUTPUT_FORMAT = "pdfa";

    private static final Option OPTION_REGISTRY = new Option("r", "registry", true, "document registry");
    private static final Option OPTION_OUTPUT_FORMAT = new Option("f", "output-format", true, "output format (e.g. pdf)");
    private static final Option OPTION_PORT = new Option("p", "port", true, "OpenOffice.org port");
    private static final Option OPTION_VERBOSE = new Option("v", "verbose", false, "verbose");

    private static final Options OPTIONS = initOptions();

    private static Options initOptions() {
        Options options = new Options();
        options.addOption(OPTION_OUTPUT_FORMAT);
        options.addOption(OPTION_PORT);
        options.addOption(OPTION_VERBOSE);
        options.addOption(OPTION_REGISTRY);
        return options;
    }

    public static void main(String[] arguments) throws Exception {
        CommandLineParser commandLineParser = new PosixParser();
        CommandLine commandLine = commandLineParser.parse(OPTIONS, arguments);

        int port = SocketOpenOfficeConnection.DEFAULT_PORT;
        if (commandLine.hasOption(OPTION_PORT.getOpt())) {
            port = Integer.parseInt(commandLine.getOptionValue(OPTION_PORT.getOpt()));
        }

        String outputFormat = DEFAULT_OUTPUT_FORMAT;
        if (commandLine.hasOption(OPTION_OUTPUT_FORMAT.getOpt())) {
            outputFormat = commandLine.getOptionValue(OPTION_OUTPUT_FORMAT.getOpt());
        }

        boolean verbose = false;
        if (commandLine.hasOption(OPTION_VERBOSE.getOpt())) {
            verbose = true;
        }

        String formatRegistry = DEFAULT_FORMAT_REGISTRY;
        if (commandLine.hasOption(OPTION_REGISTRY.getOpt())) {
            formatRegistry = commandLine.getOptionValue(OPTION_REGISTRY.getOpt());
            if (verbose) {
                System.err.println("-- document format registry is " + formatRegistry);
            }
        }

        String[] dirNames = commandLine.getArgs();
        if (dirNames.length != 2) {
            String syntax = "convertdoc [options] input-directory output-directory";
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(syntax, OPTIONS);
            System.exit(1);
        }
        String inputDirName = dirNames[0];
        String outputDirName = dirNames[1];

        OpenOfficeConnection connection = new SocketOpenOfficeConnection(port);
        try {
            if (verbose) {
                System.out.println("-- connecting to OpenOffice.org on port " + port);
            }
            connection.connect();
        } catch (ConnectException officeNotRunning) {
            error("connection failed. Please make sure OpenOffice.org is running and listening on port " + port + ".");
        }
        try {

            File formatRegistryFile = new File(formatRegistry);
            if (!formatRegistryFile.isFile()) {
                error("Document registry file " + formatRegistryFile.getCanonicalPath() + " doesn't exist.");
            }

            InputStream is = new FileInputStream(formatRegistryFile);
            XmlDocumentFormatRegistry xmlDocFormatRegistry = new XmlDocumentFormatRegistry(is);
            DocumentConverter converter = new OpenOfficeDocumentConverter(connection, xmlDocFormatRegistry);

            File inputDirectory = new File(inputDirName);
            File outputDirectory = new File(outputDirName);

            if (!inputDirectory.isDirectory()) {
                error("input directory " + inputDirName + " doesn't exist.");
            }

            if (!outputDirectory.isDirectory() && !outputDirectory.mkdir()) {
                error("ERROR: can't creat output directory " + outputDirName + ".");
            }

            File[] inputFiles = inputDirectory.listFiles();

            for (int i = 0; i < inputFiles.length; i++) {
                String inputFileName = inputFiles[i].getName();
                if (inputFiles[i].isDirectory() || inputFileName.startsWith(".")) {
                    if (verbose) {
                        System.out.println("-- skipping directory or dot file " + inputFiles[i]);
                    }
                    continue;
                }
                File outputFile = new File(outputDirName + File.separator
                    + FilenameUtils.getBaseName(inputFileName) + "." + outputFormat);
                convertOne(converter, inputFiles[i], outputFile, verbose);
            }

        } catch (Exception e) {
            System.err.println(e);
        } finally {
            if (verbose) {
                System.out.println("-- disconnecting");
            }
            connection.disconnect();
        }
    }

    private static void convertOne(DocumentConverter converter, File inputFile, File outputFile, boolean verbose) {
        if (verbose) {
            System.out.println("-- converting " + inputFile + " to " + outputFile);
        }
        converter.convert(inputFile, outputFile);
    }

    private static void error(String msg) {
        System.err.println("ERROR: " + msg);
        System.exit(1);
    }

}
