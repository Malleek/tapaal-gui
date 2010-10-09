package dk.aau.cs.verification.UPPAAL;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pipe.gui.FileFinder;
import pipe.gui.Pipe;
import dk.aau.cs.Messenger;
import dk.aau.cs.petrinet.TAPNQuery;
import dk.aau.cs.petrinet.TimedArcPetriNet;
import dk.aau.cs.petrinet.colors.ColoredTimedArcPetriNet;
import dk.aau.cs.verification.ModelChecker;
import dk.aau.cs.verification.ProcessRunner;
import dk.aau.cs.verification.QueryResult;
import dk.aau.cs.verification.Trace;
import dk.aau.cs.verification.VerificationOptions;
import dk.aau.cs.verification.VerificationResult;

public class Verifyta implements ModelChecker {
	private static final String NEED_TO_LOCATE_VERIFYTA_MSG = "TAPAAL needs to know the location of the file verifyta.\n\n"+
	"Verifyta is a part of the UPPAAL distribution and it is\n" +
	"normally located in uppaal/bin-Linux or uppaal/bin-Win32,\n" +
	"depending on the operating system used.";

	private static String verifytapath=""; // MJ -- Should be part of a configuration file that can be accessed
	private FileFinder fileFinder;
	private Messenger messenger;

	private ProcessRunner runner;

	public Verifyta(FileFinder fileFinder, Messenger messenger){
		this.fileFinder = fileFinder;
		this.messenger = messenger;
	}

	public boolean setup(){ // TODO: Should maybe eliminate boolean return value from here?
		if(isNotSetup()){
			messenger.displayInfoMessage(NEED_TO_LOCATE_VERIFYTA_MSG, "Locate UPPAAL Verifyta");

			try {
				File file = fileFinder.ShowFileBrowserDialog("Uppaal Verifyta","");
				verifytapath=file.getAbsolutePath();
			} catch (Exception e) {
				messenger.displayErrorMessage(
						"There were errors performing the requested action:\n" + e,
				"Error");
			}

		}

		return !isNotSetup();
	}

	public String getPath(){
		return verifytapath; // TODO: MJ -- delete me
	}

	public String getVersion(){
		String result = null;

		if (!isNotSetup()){
			String[] commands;
			commands = new String[]{verifytapath, "-v"};

			InputStream stream = null;
			try {
				Process child = Runtime.getRuntime().exec(commands);
				child.waitFor();
				stream = child.getInputStream();
			} catch (IOException e){
			} catch (InterruptedException e) {
			}

			if(stream != null){
				result = readVersionNumberFrom(stream);
			}
		}

		return result;
	}

	public boolean isCorrectVersion(){
		if (isNotSetup()){
			messenger.displayErrorMessage("No verifyta specified: The verification is cancelled",
			"Verification Error");
			return false;
		}

		String versionAsString = getVersion();

		if(versionAsString == null){
			messenger.displayErrorMessage(
					"The program can not be verified as being verifyta.\n" +
					"The verifyta path will be reset. Please try again, " + 
					"to manually set the verifyta path.",
			"Verifyta Error");
			resetVerifyta();
			return false;
		}else{		
			int version = Integer.parseInt(versionAsString);

			if (version < Pipe.verifytaMinRev){
				messenger.displayErrorMessage(
						"The specified version of the file verifyta is too old.\n\n" +
						"Get the latest development version of UPPAAL from \n" +
						"www.uppaal.com.",
				"Verifyta Error");
				resetVerifyta();
				return false;
			}
		}

		return true;
	}

	private void resetVerifyta() {
		verifytapath = null;	
	}

	private boolean isNotSetup() {
		return verifytapath == null || verifytapath.equals("");
	}

	private String readVersionNumberFrom(InputStream stream) {
		String result = null;
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));

		String versioninfo = null;
		try {
			versioninfo = bufferedReader.readLine();
		} catch (IOException e) {
			result = null;
		}

		Pattern pattern = Pattern.compile("\\(rev. (\\d+)\\)");
		Matcher m =  pattern.matcher(versioninfo);
		m.find();
		result =  m.group(1);
		return result;
	}

	public static boolean trySetupFromEnvironmentVariable() {
		String verifyta = System.getenv("verifyta");
		if(verifyta != null && !verifyta.equals("")){
			verifytapath = verifyta;
			return true;
		}
		return false;
	}


	private String createArgumentString(String modelFile, String queryFile, VerificationOptions options){
		StringBuffer buffer = new StringBuffer(options.toString());
		buffer.append(" ");
		buffer.append(modelFile);
		buffer.append(" ");
		buffer.append(queryFile);

		return buffer.toString();
	}

	private VerificationResult verify(VerificationOptions options, ExportedModel exportedModel) {
		runner = new ProcessRunner(verifytapath, createArgumentString(exportedModel.modelFile(), exportedModel.queryFile(), options));
		runner.run();

		if(runner.error()){
			return null;
		}else{			
			VerifytaOutputParser outputParser = new VerifytaOutputParser();
			QueryResult[] results = outputParser.parseOutput(runner.standardOutput());

			VerifytaTraceParser traceParser = new VerifytaTraceParser();
			Trace[] traces = traceParser.parseTrace(runner.errorOutput());
			
			VerificationResult result = new VerificationResult(results, traces);

			return result;
		}
	}

	public VerificationResult verify(VerificationOptions options, TimedArcPetriNet model, TAPNQuery query){
		UppaalExporter exporter = new UppaalExporter();
		ExportedModel exportedModel = null;
		if(model instanceof ColoredTimedArcPetriNet){
			exportedModel = exporter.export((ColoredTimedArcPetriNet)model, query, ((VerifytaOptions)options).getReduction());
		}else{
			exportedModel = exporter.export(model, query, ((VerifytaOptions)options).getReduction());
		}
		
		if(exportedModel == null){
			messenger.displayErrorMessage("There was an error exporting the model");
		}
						
		return verify(options, exportedModel);
	}

	public void kill(){
		if(runner != null){
			runner.kill();
		}
	}
}