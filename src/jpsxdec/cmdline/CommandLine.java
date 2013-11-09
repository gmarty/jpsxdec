/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2013  Michael Sabin
 * All rights reserved.
 *
 * Redistribution and use of the jPSXdec code or any derivative works are
 * permitted provided that the following conditions are met:
 *
 *  * Redistributions may not be sold, nor may they be used in commercial
 *    or revenue-generating business activities.
 *
 *  * Redistributions that are modified from the original source must
 *    include the complete source code, including the source code for all
 *    components used by a binary built from the modified sources. However, as
 *    a special exception, the source code distributed need not include
 *    anything that is normally distributed (in either source or binary form)
 *    with the major components (compiler, kernel, and so on) of the operating
 *    system on which the executable runs, unless that component itself
 *    accompanies the executable.
 *
 *  * Redistributions must reproduce the above copyright notice, this list
 *    of conditions and the following disclaimer in the documentation and/or
 *    other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jpsxdec.cmdline;

import argparser.ArgParser;
import argparser.BooleanHolder;
import argparser.StringHolder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import jpsxdec.Version;
import jpsxdec.cdreaders.CdFileNotFoundException;
import jpsxdec.cdreaders.CdFileSectorReader;
import jpsxdec.indexing.DiscIndex;
import jpsxdec.util.ConsoleProgressListenerLogger;
import jpsxdec.util.FeedbackStream;
import jpsxdec.util.TaskCanceledException;


public class CommandLine {
    
    private static final Logger LOG = Logger.getLogger(CommandLine.class.getName());

    public static int main(String[] asArgs) {

        FeedbackStream Feedback = new FeedbackStream(System.out, FeedbackStream.NORM);

        asArgs = checkVerbosity(asArgs, Feedback);

        Feedback.println(Version.VerStringNonCommercial);

        ArgParser ap = new ArgParser("", false);

        StringHolder inputFileArg = new StringHolder();
        ap.addOption("-f,-file %s", inputFileArg);

        StringHolder indexFileArg = new StringHolder();
        ap.addOption("-x,-index %s", indexFileArg);

        Command[] aoCommands = {
            new Command_CopySect(),
            new Command_SectorDump(),
            new Command_Static(),
            new Command_Visualize(),
            new Command_Items.Command_Item(),
            new Command_Items.Command_All(),
        };

        for (Command command : aoCommands) {
            command.init(ap, inputFileArg, indexFileArg, Feedback);
        }

        asArgs = ap.matchAllArgs(asArgs, 0, 0);

        String sErr = ap.getErrorMessage();
        if (sErr != null) {
            Feedback.printlnErr("Error: " + sErr);
            Feedback.printlnErr("Try -? for help.");
            return 1;
        }

        Command mainCommand = null;
        for (Command command : aoCommands) {
            if(command.found()) {
                if (mainCommand != null) {
                    Feedback.printlnErr("Too many main commands.");
                    Feedback.printlnErr("Try -? for help.");
                    return 1;
                }
                mainCommand = command;
            }
        }

        try {
            if (mainCommand == null) {
                if (checkForMainHelp(asArgs)) {
                    printMainHelp(Feedback);
                } else {
                    if (inputFileArg.value != null && indexFileArg.value != null) {
                        createAndSaveIndex(inputFileArg.value, indexFileArg.value, Feedback);
                    } else {
                        Feedback.printlnErr("Need a main command.");
                        Feedback.printlnErr("Try -? for help.");
                        return 1;
                    }
                }
            } else {
                String sError = mainCommand.validate();
                if (sError != null) {
                    Feedback.printlnErr(sError);
                    Feedback.printlnErr("Try -? for help.");
                    return 1;
                } else {
                    mainCommand.execute(asArgs);
                }
            }
        } catch (CommandLineException ex) {
            ex.printError(Feedback);
            LOG.log(Level.SEVERE, null, ex);
            return 1;
        } catch (Throwable ex) {
            Feedback.printlnErr("Error: " + ex.toString() + " (" + ex.getClass().getSimpleName() + ")");
            LOG.log(Level.SEVERE, "Unhandled exception", ex);
            return 1;
        }
        return 0;
    }

    // -------------------------------------------------------------
    
    private static String[] checkVerbosity(String[] asArgs, FeedbackStream fbs) {
        ArgParser ap = new ArgParser("", false);

        StringHolder verbose = new StringHolder();
        ap.addOption("-v,-verbose %s", verbose);
        asArgs = ap.matchAllArgs(asArgs, 0, 0);

        if (verbose.value != null) {
            try {
                int iValue = Integer.parseInt(verbose.value);
                if (iValue >= FeedbackStream.NONE && iValue <= FeedbackStream.MORE)
                    fbs.setLevel(iValue);
                else
                    fbs.printlnWarn("Invalid verbosity level " + iValue);
            } catch (NumberFormatException ex) {
                fbs.printlnWarn("Invalid verbosity level " + verbose.value);
            }
        }

        return asArgs;
    }
    
    public static boolean checkForMainHelp(String[] asArgs) {
        if (asArgs == null)
            return false;

        ArgParser ap = new ArgParser("", false);

        BooleanHolder help = new BooleanHolder();
        ap.addOption("-?,-h,-help %v", help);
        ap.matchAllArgs(asArgs, 0, 0);

        return help.value;
    }

    private static void printMainHelp(PrintStream ps) {
        InputStream is = CommandLine.class.getResourceAsStream("main_cmdline_help.dat");
        if (is == null)
            throw new RuntimeException("Unable to find help resource " +
                    CommandLine.class.getResource("main_cmdline_help.dat"));
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        try {
            String sLine;
            while ((sLine = br.readLine()) != null) {
                ps.println(sLine);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
    }

    // -------------------------------------------------------------

    private static void createAndSaveIndex(String sDiscFile, String sIndexFile,
                                           FeedbackStream Feedback)
            throws CommandLineException
    {
        CdFileSectorReader cd = loadDisc(sDiscFile, Feedback);
        try {
            DiscIndex index = buildIndex(cd, Feedback);
            saveIndex(index, sIndexFile, Feedback);
        } finally {
            try {
                cd.close();
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Error closing CD", ex);
            }
        }
    }

    static CdFileSectorReader loadDisc(String sDiscFile, FeedbackStream Feedback) 
            throws CommandLineException
    {
        if (sDiscFile == null)
            throw new CommandLineException("Command need disc file");
        Feedback.println("Opening " + sDiscFile);
        try {
            CdFileSectorReader cd = new CdFileSectorReader(new File(sDiscFile));
            Feedback.println("Identified as " + cd.getTypeDescription());
            return cd;
        } catch (CdFileNotFoundException ex) {
            throw new CommandLineException("File not found.", ex.getFile());
        } catch (IOException ex) {
            throw new CommandLineException("Disc read error.", ex);
        }
    }

    static DiscIndex buildIndex(CdFileSectorReader cd, FeedbackStream Feedback) {
        Feedback.println("Building index");
        DiscIndex index = null;
        ConsoleProgressListenerLogger cpll = new ConsoleProgressListenerLogger("index", Feedback);
        try {
            cpll.setHeader(1, "Indexing " + cd.toString());
            index = new DiscIndex(cd, cpll);
        } catch (TaskCanceledException ex) {
            throw new RuntimeException("Impossible TaskCanceledException during commandline indexing");
        } finally {
            cpll.close();
        }
        Feedback.println(index.size() + " items found.");
        return index;
    }

    static void saveIndex(DiscIndex index, String sIndexFile, FeedbackStream Feedback) 
            throws CommandLineException
    {
        if (index.size() < 1) {
            Feedback.println("No items found, not saving index file.");
        } else if (sIndexFile.equals("-")) {
            try {
                index.serializeIndex(System.out);
            } catch (IOException ex) {
                throw new CommandLineException("Error writing index file.", ex);
            }
        } else {
            Feedback.println("Saving index as " + sIndexFile);
            PrintStream printer = null;
            try {
                printer = new PrintStream(sIndexFile);
                index.serializeIndex(printer);
            } catch (FileNotFoundException ex) {
                throw new CommandLineException("Error opening file for saving", ex);
            } catch (IOException ex) {
                throw new CommandLineException("Error writing index file.", ex);
            } finally {
                if (printer != null)
                    printer.close();
            }
        }
    }


}
