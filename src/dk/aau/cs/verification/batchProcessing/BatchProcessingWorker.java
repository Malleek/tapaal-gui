package dk.aau.cs.verification.batchProcessing;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;

import pipe.dataLayer.TAPNQuery.SearchOption;
import pipe.dataLayer.TAPNQuery.TraceOption;
import pipe.gui.FileFinderImpl;
import pipe.gui.MessengerImpl;
import pipe.gui.widgets.InclusionPlaces;
import dk.aau.cs.Messenger;
import dk.aau.cs.TCTL.TCTLAGNode;
import dk.aau.cs.TCTL.TCTLAbstractProperty;
import dk.aau.cs.TCTL.TCTLTrueNode;
import dk.aau.cs.TCTL.visitors.RenameAllPlacesVisitor;
import dk.aau.cs.TCTL.visitors.SimplifyPropositionsVisitor;
import dk.aau.cs.approximation.OverApproximation;
import dk.aau.cs.approximation.UnderApproximation;
import dk.aau.cs.gui.BatchProcessingDialog;
import dk.aau.cs.gui.components.BatchProcessingResultsTableModel;
import dk.aau.cs.io.batchProcessing.BatchProcessingModelLoader;
import dk.aau.cs.io.batchProcessing.LoadedBatchProcessingModel;
import dk.aau.cs.model.tapn.TAPNQuery;
import dk.aau.cs.model.tapn.TimedArcPetriNet;
import dk.aau.cs.model.tapn.TimedInhibitorArc;
import dk.aau.cs.model.tapn.TimedInputArc;
import dk.aau.cs.model.tapn.TimedOutputArc;
import dk.aau.cs.model.tapn.TimedPlace;
import dk.aau.cs.model.tapn.TimedToken;
import dk.aau.cs.model.tapn.TimedTransition;
import dk.aau.cs.model.tapn.TransportArc;
import dk.aau.cs.model.tapn.simulation.TAPNNetworkTrace;
import dk.aau.cs.model.tapn.simulation.TimeDelayStep;
import dk.aau.cs.model.tapn.simulation.TimedArcPetriNetStep;
import dk.aau.cs.model.tapn.simulation.TimedArcPetriNetTrace;
import dk.aau.cs.model.tapn.simulation.TimedTransitionStep;
import dk.aau.cs.translations.ReductionOption;
import dk.aau.cs.util.MemoryMonitor;
import dk.aau.cs.util.Require;
import dk.aau.cs.util.Tuple;
import dk.aau.cs.util.UnsupportedModelException;
import dk.aau.cs.util.UnsupportedQueryException;
import dk.aau.cs.verification.Boundedness;
import dk.aau.cs.verification.ITAPNComposer;
import dk.aau.cs.verification.ModelChecker;
import dk.aau.cs.verification.NameMapping;
import dk.aau.cs.verification.NullStats;
import dk.aau.cs.verification.QueryResult;
import dk.aau.cs.verification.QueryType;
import dk.aau.cs.verification.Stats;
import dk.aau.cs.verification.TAPNComposer;
import dk.aau.cs.verification.TAPNComposerExtended;
import dk.aau.cs.verification.TAPNTraceDecomposer;
import dk.aau.cs.verification.VerificationOptions;
import dk.aau.cs.verification.VerificationResult;
import dk.aau.cs.verification.UPPAAL.Verifyta;
import dk.aau.cs.verification.UPPAAL.VerifytaOptions;
import dk.aau.cs.verification.VerifyTAPN.VerifyDTAPNOptions;
import dk.aau.cs.verification.VerifyTAPN.VerifyPN;
import dk.aau.cs.verification.VerifyTAPN.VerifyPNOptions;
import dk.aau.cs.verification.VerifyTAPN.VerifyTAPN;
import dk.aau.cs.verification.VerifyTAPN.VerifyTAPNDiscreteVerification;
import dk.aau.cs.verification.VerifyTAPN.VerifyTAPNOptions;
import dk.aau.cs.verification.batchProcessing.BatchProcessingVerificationOptions.QueryPropertyOption;
import dk.aau.cs.verification.batchProcessing.BatchProcessingVerificationOptions.SymmetryOption;


public class BatchProcessingWorker extends SwingWorker<Void, BatchProcessingVerificationResult> {
	private List<File> files;
	private final BatchProcessingResultsTableModel tableModel;
	private BatchProcessingVerificationOptions batchProcessingVerificationOptions;
	private boolean isExiting = false;
	private ModelChecker modelChecker;
	List<BatchProcessingListener> listeners = new ArrayList<BatchProcessingListener>();
	private boolean skippingCurrentVerification = false;
	private boolean timeoutCurrentVerification = false;
	private boolean oomCurrentVerification = false;
	private int verificationTasksCompleted;
	private LoadedBatchProcessingModel model;
	
	
	public BatchProcessingWorker(List<File> files, BatchProcessingResultsTableModel tableModel, BatchProcessingVerificationOptions batchProcessingVerificationOptions) {
		super();
		this.files = files;
		this.tableModel = tableModel;
		this.batchProcessingVerificationOptions = batchProcessingVerificationOptions;
		
	}

	public synchronized void notifyExiting(){
		isExiting = true;
	}
	
	private synchronized boolean exiting(){
		return isExiting;
	}
	
	public synchronized void notifySkipCurrentVerification() {
		skippingCurrentVerification = true;
		if(modelChecker != null) {
			modelChecker.kill();
		}
	}
	
	public synchronized void notifyTimeoutCurrentVerificationTask() {
		timeoutCurrentVerification = true;
		if(modelChecker != null) {
			modelChecker.kill();
		}
	}
	
	public synchronized void notifyOOMCurrentVerificationTask() {
		oomCurrentVerification = true;
		if(modelChecker != null) {
			modelChecker.kill();
		}
	}
	
	
	@Override
	protected Void doInBackground() throws Exception {
		for(File file : files){

			fireFileChanged(file.getName());
			LoadedBatchProcessingModel model = loadModel(file);
			this.model = model;
			if(model != null) {			
				for(pipe.dataLayer.TAPNQuery query : model.queries()) {
                    if(exiting()) {
                        return null;
                    }			
                    Tuple<TimedArcPetriNet, NameMapping> composedModel = composeModel(model);
                                        
					pipe.dataLayer.TAPNQuery queryToVerify = overrideVerificationOptions(composedModel.value1(), query);
					
					if (batchProcessingVerificationOptions.isReductionOptionUserdefined()){
						processQueryForUserdefinedReductions(file,composedModel, queryToVerify);
					} else {
						processQuery(file, composedModel, queryToVerify);
					}
					
				}
			}
		}
		fireFileChanged("");
		fireStatusChanged("");
		return null;
	}

	private void processQueryForUserdefinedReductions(File file, Tuple<TimedArcPetriNet, NameMapping> composedModel, pipe.dataLayer.TAPNQuery queryToVerify) throws Exception {
		pipe.dataLayer.TAPNQuery query = queryToVerify;
		query.setDiscreteInclusion(false);
		if(!exiting() && batchProcessingVerificationOptions.reductionOptions().contains(ReductionOption.VerifyTAPN)){
			query = query.copy();
			query.setReductionOption(ReductionOption.VerifyTAPN);
			processQuery(file, composedModel, query);
		}
		
		if(!exiting() && batchProcessingVerificationOptions.discreteInclusion()){
			query = query.copy();
			query.setReductionOption(ReductionOption.VerifyTAPN);
			query.setDiscreteInclusion(true);
			processQuery(file, composedModel, query);
		}
		
		//Make the PTrie/timedarts availible 
		//TODO This shold be made simpler in the engine refacter process
		query = query.copy();
		query.setDiscreteInclusion(false);
		query.setReductionOption(ReductionOption.VerifyTAPNdiscreteVerification);
		if(!exiting() && batchProcessingVerificationOptions.useTimeDartPTrie()){
			query = query.copy();
			query.setUseTimeDarts(true);
			query.setUsePTrie(true);
			processQuery(file, composedModel, query);
		}
		
		if(!exiting() && batchProcessingVerificationOptions.useTimeDart()){
			query = query.copy();
			query.setUseTimeDarts(true);
			query.setUsePTrie(false);
			processQuery(file, composedModel, query);
		}
		
		if(!exiting() && batchProcessingVerificationOptions.usePTrie()){
			query = query.copy();
			query.setUseTimeDarts(false);
			query.setUsePTrie(true);
			processQuery(file, composedModel, query);
		}
		
		if(!exiting() && batchProcessingVerificationOptions.reductionOptions().contains(ReductionOption.VerifyTAPNdiscreteVerification)){
			query = query.copy();
			query.setUseTimeDarts(false);
			query.setUsePTrie(false);
			processQuery(file, composedModel, query);
		}
		
		// VerifyTA reductions
		query = query.copy();
		query.setDiscreteInclusion(false);
		for(ReductionOption r : batchProcessingVerificationOptions.reductionOptions()){
			if(r == ReductionOption.VerifyTAPN || r == ReductionOption.VerifyTAPNdiscreteVerification || r == ReductionOption.VerifyPNApprox || r == ReductionOption.VerifyPN) { continue; }
			if(exiting()) return;
			query = query.copy();
			query.setReductionOption(r);
			processQuery(file, composedModel, query);
		}
		
		// VerifyPN reductions
		if(!exiting() && batchProcessingVerificationOptions.reductionOptions().contains(ReductionOption.VerifyPN)){
			query = query.copy();
			query.setReductionOption(ReductionOption.VerifyPN);
			query.setUseOverApproximation(false);
			processQuery(file, composedModel, query);
		}
		
		if(!exiting() && batchProcessingVerificationOptions.reductionOptions().contains(ReductionOption.VerifyPNApprox)){
			query = query.copy();
			query.setReductionOption(ReductionOption.VerifyPNApprox);
			query.setUseOverApproximation(true);
			processQuery(file, composedModel, query);
		}
	}

	private void processQuery(File file, Tuple<TimedArcPetriNet, NameMapping> composedModel, pipe.dataLayer.TAPNQuery queryToVerify) throws Exception {
		if(queryToVerify.isActive()) { 
			VerificationResult<TimedArcPetriNetTrace> verificationResult = verifyQuery(file, composedModel, queryToVerify);
			
			if(verificationResult != null)
				processVerificationResult(file, queryToVerify, verificationResult);
		}
		else
			publishResult(file.getName(), queryToVerify, "Skipped - query is disabled because it contains propositions involving places from a deactivated component", 0, new NullStats());
		fireVerificationTaskComplete();
	}

	private pipe.dataLayer.TAPNQuery overrideVerificationOptions(TimedArcPetriNet model, pipe.dataLayer.TAPNQuery query) throws Exception {
		if(batchProcessingVerificationOptions != null) {
			SearchOption search = batchProcessingVerificationOptions.searchOption() == SearchOption.BatchProcessingKeepQueryOption ? query.getSearchOption() : batchProcessingVerificationOptions.searchOption();
			ReductionOption option = query.getReductionOption();
			TCTLAbstractProperty property = batchProcessingVerificationOptions.queryPropertyOption() == QueryPropertyOption.KeepQueryOption ? query.getProperty() : generateSearchWholeStateSpaceProperty(model);
			boolean symmetry = batchProcessingVerificationOptions.symmetry() == SymmetryOption.KeepQueryOption ? query.useSymmetry() : getSymmetryFromBatchProcessingOptions();
			int capacity = batchProcessingVerificationOptions.KeepCapacityFromQuery() ? query.getCapacity() : batchProcessingVerificationOptions.capacity();
			String name = batchProcessingVerificationOptions.queryPropertyOption() == QueryPropertyOption.KeepQueryOption ? query.getName() : "Search Whole State Space";
			
			pipe.dataLayer.TAPNQuery changedQuery = new pipe.dataLayer.TAPNQuery(name, capacity, property, TraceOption.NONE, search, option, symmetry, true, query.useTimeDarts(), query.usePTrie(), query.useOverApproximation(),  query.getHashTableSize(), query.getExtrapolationOption(), query.inclusionPlaces(), query.isOverApproximationEnabled(), query.isUnderApproximationEnabled(), query.approximationDenominator());
			
			if(batchProcessingVerificationOptions.queryPropertyOption() == QueryPropertyOption.KeepQueryOption)
				changedQuery.setActive(query.isActive());
			
			simplifyQuery(changedQuery);
			return changedQuery;
		}
		
		return query;
	}
	
	private void simplifyQuery(pipe.dataLayer.TAPNQuery query) {
		SimplifyPropositionsVisitor visitor = new SimplifyPropositionsVisitor();
		visitor.findAndReplaceTrueAndFalsePropositions(query.getProperty());
	}

	private boolean getSymmetryFromBatchProcessingOptions() {
		return batchProcessingVerificationOptions.symmetry() == SymmetryOption.Yes;
	}

	private Tuple<TimedArcPetriNet, NameMapping> composeModel(LoadedBatchProcessingModel model) {
		ITAPNComposer composer = new TAPNComposer(new Messenger(){
			public void displayInfoMessage(String message) { }
			public void displayInfoMessage(String message, String title) {}
			public void displayErrorMessage(String message) {}
			public void displayErrorMessage(String message, String title) {}
			public void displayWrappedErrorMessage(String message, String title) {}
			
		});
		Tuple<TimedArcPetriNet, NameMapping> composedModel = composer.transformModel(model.network());
		return composedModel;
	}

	private VerificationResult<TimedArcPetriNetTrace> verifyQuery(File file, Tuple<TimedArcPetriNet, NameMapping> composedModel, pipe.dataLayer.TAPNQuery query) throws Exception {
		fireStatusChanged(query.getName());
		
		VerificationResult<TimedArcPetriNetTrace> verificationResult = null;
		try {
			verificationResult = verify(composedModel, query);
		} catch(UnsupportedModelException e) {
			publishResult(file.getName(), query, "Skipped - model not supported by the verification method", 0, new NullStats());
			return null;
		} catch(UnsupportedQueryException e) {
			if(e.getMessage().toLowerCase().contains("discrete inclusion"))
				publishResult(file.getName(), query, "Skipped - discrete inclusion is enabled and query is not upward closed", 0, new NullStats());
			else
				publishResult(file.getName(), query, "Skipped - query not supported by the verification method", 0, new NullStats());
			return null;
		}
		return verificationResult;
	}

	private void processVerificationResult(File file, pipe.dataLayer.TAPNQuery query, VerificationResult<TimedArcPetriNetTrace> verificationResult) {
		if(skippingCurrentVerification) {
			publishResult(file.getName(), query, "Skipped - by the user", verificationResult.verificationTime(), new NullStats());
			skippingCurrentVerification = false;
		} else if(timeoutCurrentVerification) {
			publishResult(file.getName(), query, "Skipped - due to timeout", verificationResult.verificationTime(), new NullStats());
			timeoutCurrentVerification = false;
		} else if(oomCurrentVerification) {
			publishResult(file.getName(), query, "Skipped - due to OOM", verificationResult.verificationTime(), new NullStats());
			oomCurrentVerification = false;
		} else if(!verificationResult.error()) {
			String queryResult = "";
			if (verificationResult.getQueryResult().isApproximationInconclusive())
			{
				queryResult = "Inconclusive";
			}
			else
			{
				queryResult = verificationResult.getQueryResult().isQuerySatisfied() ? "Satisfied" : "Not Satisfied";
			}
			if (query.discreteInclusion() && !verificationResult.isBounded() && 
					((query.queryType().equals(QueryType.EF) && !verificationResult.getQueryResult().isQuerySatisfied())
					||
					(query.queryType().equals(QueryType.AG) && verificationResult.getQueryResult().isQuerySatisfied())))
			{queryResult = "Inconclusive answer";}
		publishResult(file.getName(), query, queryResult,	verificationResult.verificationTime(), verificationResult.stats());
		} else {
			publishResult(file.getName(), query, "Error during verification", verificationResult.verificationTime(), new NullStats());
		}		
	}

	private void publishResult(String fileName, pipe.dataLayer.TAPNQuery query, String verificationResult, long verificationTime, Stats stats) {
		BatchProcessingVerificationResult result = new BatchProcessingVerificationResult(fileName, query, verificationResult, verificationTime, MemoryMonitor.getPeakMemory(), stats);
		publish(result);
	}

	
	private void renameTraceTransitions(TimedArcPetriNetTrace trace) {
		if (trace != null)
			trace.reduceTraceForOriginalNet("_traceNet_", "PTRACE");
	}

	private TAPNNetworkTrace decomposeTrace(TimedArcPetriNetTrace trace, NameMapping mapping) {
		if (trace == null)
			return null;

		TAPNTraceDecomposer decomposer = new TAPNTraceDecomposer(trace, model.network(), mapping);
		return decomposer.decompose();
	}
	
	private VerificationResult<TimedArcPetriNetTrace> verify(Tuple<TimedArcPetriNet, NameMapping> composedModel, pipe.dataLayer.TAPNQuery query) throws Exception {
		MemoryMonitor.setCumulativePeakMemory(true);
		
		TAPNQuery queryToVerify = getTAPNQuery(composedModel.value1(),query);
		MapQueryToNewNames(queryToVerify, composedModel.value2());
		
		TAPNQuery clonedQuery = new TAPNQuery(query.getProperty().copy(), queryToVerify.getExtraTokens());
		MapQueryToNewNames(clonedQuery, composedModel.value2());
		
		VerificationOptions options = getVerificationOptionsFromQuery(query);
		
		Tuple<TimedArcPetriNet, NameMapping> transformedOriginalModel = new Tuple<TimedArcPetriNet, NameMapping>(composedModel.value1().copy(), composedModel.value2());
		
		TraceOption oldTraceOption = options.traceOption();
		if (query != null && query.isOverApproximationEnabled())
		{
			OverApproximation overaprx = new OverApproximation();
			overaprx.modifyTAPN(composedModel.value1(), query);
			options.setTraceOption(TraceOption.SOME);
		}
		else if (query != null && query.isUnderApproximationEnabled())
		{
			UnderApproximation underaprx = new UnderApproximation();
			underaprx.modifyTAPN(composedModel.value1(), query);
		}
		
		modelChecker = getModelChecker(query);
		fireVerificationTaskStarted();
		VerificationResult<TimedArcPetriNetTrace> verificationResult = modelChecker.verify(options, composedModel, queryToVerify);		
		
		InclusionPlaces oldInclusionPlaces = null;
		if (options instanceof VerifyTAPNOptions)
			oldInclusionPlaces = ((VerifyTAPNOptions) options).inclusionPlaces();
		
		VerificationResult<TAPNNetworkTrace> valueNetwork = null;	//The final result is meant to be a PetriNetTrace but to make traceTAPN we make a networktrace
		VerificationResult<TimedArcPetriNetTrace> value = null;
		if (verificationResult.error()) {
			return new VerificationResult<TimedArcPetriNetTrace>(verificationResult.errorMessage(), verificationResult.verificationTime());
		}
		else if (query != null && query.isOverApproximationEnabled()) {		
			//Create the verification satisfied result for the approximation
            
            // Over-approximation
			if (query.approximationDenominator() == 1) {
                // If r = 1
                // No matter what EF and AG answered -> return that answer
               QueryResult queryResult = verificationResult.getQueryResult();
               value =  new VerificationResult<TimedArcPetriNetTrace>(
					queryResult,
					verificationResult.getTrace(),
					verificationResult.getSecondaryTrace(),
					verificationResult.verificationTime(),
					verificationResult.stats(),
					verificationResult.isOverApproximationResult());
				value.setNameMapping(composedModel.value2());
	        } else {
	            // If r > 1
	            if ((verificationResult.getQueryResult().queryType() == QueryType.EF && verificationResult.getQueryResult().isQuerySatisfied())
	               || (verificationResult.getQueryResult().queryType() == QueryType.AG && !verificationResult.getQueryResult().isQuerySatisfied())) {
	                //Create the verification satisfied result for the approximation
	                VerificationResult<TimedArcPetriNetTrace> approxResult = verificationResult;
	                valueNetwork = new VerificationResult<TAPNNetworkTrace>(
	                            approxResult.getQueryResult(),
	                            decomposeTrace(approxResult.getTrace(), composedModel.value2()),
	                            decomposeTrace(approxResult.getSecondaryTrace(), composedModel.value2()),
	                            approxResult.verificationTime(),
	                            approxResult.stats(),
	        					verificationResult.isOverApproximationResult());
	                valueNetwork.setNameMapping(composedModel.value2());
	                
	                OverApproximation overaprx = new OverApproximation();
	                
	                //Create trace TAPN from the network trace
	                overaprx.makeTraceTAPN(transformedOriginalModel, valueNetwork, clonedQuery);
	                
	                // Reset the inclusion places in order to avoid NullPointerExceptions
	                if (options instanceof VerifyTAPNOptions && oldInclusionPlaces != null)
	                    ((VerifyTAPNOptions) options).setInclusionPlaces(oldInclusionPlaces);

	                //run model checker again for trace TAPN
	                verificationResult = modelChecker.verify(options, transformedOriginalModel, clonedQuery);
	                if (isCancelled()) {
	                    firePropertyChange("state", StateValue.PENDING, StateValue.DONE);
	                }
	                if (verificationResult.error()) {
	                    return new VerificationResult<TimedArcPetriNetTrace>(verificationResult.errorMessage(), verificationResult.verificationTime());
	                }
	                //Create the result from trace TAPN
	                renameTraceTransitions(verificationResult.getTrace());
	                renameTraceTransitions(verificationResult.getSecondaryTrace());
	                QueryResult queryResult= verificationResult.getQueryResult();
	                
	                // If (EG AND not satisfied trace) OR (AG AND satisfied trace) -> inconclusive
	                if ((verificationResult.getQueryResult().queryType() == QueryType.EF && !queryResult.isQuerySatisfied()) 
	                    || verificationResult.getQueryResult().queryType() == QueryType.AG && queryResult.isQuerySatisfied()){
	                    queryResult.setApproximationInconclusive(true);
	                }
	                // If (EF AND satisfied trace) OR (AG AND satisfied trace) -> Return result
	                // This is satisfied for EF and not satisfied for AG
	                value = new VerificationResult<TimedArcPetriNetTrace>(
	                        queryResult,
	                        approxResult.getTrace(),
	                        approxResult.getSecondaryTrace(),
	                        approxResult.verificationTime() + verificationResult.verificationTime(),
	                        approxResult.stats(),
	    					verificationResult.isOverApproximationResult());
	                value.setNameMapping(composedModel.value2());
	            }
	            else if ((verificationResult.getQueryResult().queryType() == QueryType.EF && !verificationResult.getQueryResult().isQuerySatisfied())
	                  || (verificationResult.getQueryResult().queryType() == QueryType.AG && verificationResult.getQueryResult().isQuerySatisfied())) {
	                // If (EF AND not satisfied) OR (AG AND satisfied)
	               
	                QueryResult queryResult = verificationResult.getQueryResult();
	                if (clonedQuery.hasDeadlock()) {
	                    // If query has deadlock -> return inconclusive
	                    // Otherwise -> return answer
	                    queryResult.setApproximationInconclusive(true);
	                }
	                
	                value =  new VerificationResult<TimedArcPetriNetTrace>(
						queryResult,
						verificationResult.getTrace(),
						verificationResult.getSecondaryTrace(),
						verificationResult.verificationTime(),
						verificationResult.stats(),
						verificationResult.isOverApproximationResult());
				    value.setNameMapping(composedModel.value2());
	            }
	        }
	    } 
	    else if (query != null && query.isUnderApproximationEnabled()) {
	        // Under-approximation
			
	        if (query.approximationDenominator() == 1) { 
	            // If r = 1
	            // No matter what EF and AG answered -> return that answer
	            QueryResult queryResult= verificationResult.getQueryResult();
	            value =  new VerificationResult<TimedArcPetriNetTrace>(
                    queryResult,
                    verificationResult.getTrace(),
                    verificationResult.getSecondaryTrace(),
                    verificationResult.verificationTime(),
                    verificationResult.stats(),
					verificationResult.isOverApproximationResult());
                value.setNameMapping(composedModel.value2());
	        }
	        else {
	            // If r > 1
	            if ((verificationResult.getQueryResult().queryType() == QueryType.EF && !verificationResult.getQueryResult().isQuerySatisfied()) 
	             || (verificationResult.getQueryResult().queryType() == QueryType.AG && verificationResult.getQueryResult().isQuerySatisfied())) {
                    // If (EF AND not satisfied) OR (AG and satisfied) -> Inconclusive
                    
                    QueryResult queryResult= verificationResult.getQueryResult();
                    queryResult.setApproximationInconclusive(true);
                    value =  new VerificationResult<TimedArcPetriNetTrace>(
                            queryResult,
                            verificationResult.getTrace(),
                            verificationResult.getSecondaryTrace(),
                            verificationResult.verificationTime(),
                            verificationResult.stats(),
        					verificationResult.isOverApproximationResult());
                    value.setNameMapping(composedModel.value2());
	                    
	            } else if (verificationResult.getQueryResult().queryType() == QueryType.EF && verificationResult.getQueryResult().isQuerySatisfied()
	                     || (verificationResult.getQueryResult().queryType() == QueryType.AG && ! verificationResult.getQueryResult().isQuerySatisfied())) {
	                    // (EF AND satisfied) OR (AG and not satisfied) -> Check for deadlock
	                    
	                    if (!clonedQuery.hasDeadlock()) {
	                    	QueryResult queryResult= verificationResult.getQueryResult();
	                        value =  new VerificationResult<TimedArcPetriNetTrace>(
	                            queryResult,
	                            verificationResult.getTrace(),
	                            verificationResult.getSecondaryTrace(),
	                            verificationResult.verificationTime(),
	                            verificationResult.stats(),
	        					verificationResult.isOverApproximationResult());
	                        value.setNameMapping(composedModel.value2());
	                } else {
	                    // If query does have deadlock -> create trace TAPN
	                    //Create the verification satisfied result for the approximation
	                    valueNetwork = new VerificationResult<TAPNNetworkTrace>(
	                        verificationResult.getQueryResult(),
	                        decomposeTrace(verificationResult.getTrace(), composedModel.value2()),
	                        decomposeTrace(verificationResult.getSecondaryTrace(), composedModel.value2()),
	                        verificationResult.verificationTime(),
	                        verificationResult.stats(),
	    					verificationResult.isOverApproximationResult());
	                    valueNetwork.setNameMapping(composedModel.value2());
	                    
	                    OverApproximation overaprx = new OverApproximation();
	        
	                    //Create trace TAPN from the trace
	                    overaprx.makeTraceTAPN(transformedOriginalModel, valueNetwork, clonedQuery);
	                    
	                    // Reset the inclusion places in order to avoid NullPointerExceptions
	                    if (options instanceof VerifyTAPNOptions && oldInclusionPlaces != null)
	                        ((VerifyTAPNOptions) options).setInclusionPlaces(oldInclusionPlaces);
	        
	                    //run model checker again for trace TAPN
	                    verificationResult = modelChecker.verify(options, transformedOriginalModel, clonedQuery);
	                    if (isCancelled()) {
	                        firePropertyChange("state", StateValue.PENDING, StateValue.DONE);
	                    }
	                    if (verificationResult.error()) {
	        				return new VerificationResult<TimedArcPetriNetTrace>(verificationResult.errorMessage(), verificationResult.verificationTime());
	        			}
	                    
	                    //Create the result from trace TAPN
	                    renameTraceTransitions(verificationResult.getTrace());
	                    renameTraceTransitions(verificationResult.getSecondaryTrace());
	                    QueryResult queryResult = verificationResult.getQueryResult();
	                    
	                    // If (EF AND not satisfied trace) OR (AG AND satisfied trace) -> inconclusive
	                    if ((verificationResult.getQueryResult().queryType() == QueryType.EF && !queryResult.isQuerySatisfied())
	                        || verificationResult.getQueryResult().queryType() == QueryType.AG && queryResult.isQuerySatisfied()) {
	                        queryResult.setApproximationInconclusive(true);
	                    }
	                    
	                    
	                    // If (EF AND satisfied trace) OR (AG AND satisfied trace) -> Return result
	                    // This is satisfied for EF and not satisfied for AG
	                   value =  new VerificationResult<TimedArcPetriNetTrace>(
	                		    queryResult,
	                            verificationResult.getTrace(),
	                            verificationResult.getSecondaryTrace(),
	                            verificationResult.verificationTime(),
	                            verificationResult.stats(),
	        					verificationResult.isOverApproximationResult());
	                    value.setNameMapping(composedModel.value2());
	                }
	            }
	        }
	    } else {
	        value =  new VerificationResult<TimedArcPetriNetTrace>(
	                verificationResult.getQueryResult(),
	                verificationResult.getTrace(),
	                verificationResult.getSecondaryTrace(),
	                verificationResult.verificationTime(),
	                verificationResult.stats(),
					verificationResult.isOverApproximationResult());
	        value.setNameMapping(composedModel.value2());
	    }
		
		options.setTraceOption(oldTraceOption);
		MemoryMonitor.setCumulativePeakMemory(false);
		return value;
	}

	private TAPNQuery getTAPNQuery(TimedArcPetriNet model, pipe.dataLayer.TAPNQuery query) throws Exception {
		return new TAPNQuery(query.getProperty().copy(), query.getCapacity());
	}

	private TCTLAbstractProperty generateSearchWholeStateSpaceProperty(TimedArcPetriNet model) throws Exception {
		TimedPlace p = model.places().iterator().next();
		if(p == null)
			throw new Exception("Model contains no places. This may not happen.");
		
		return new TCTLAGNode(new TCTLTrueNode());
	}

	private ModelChecker getModelChecker(pipe.dataLayer.TAPNQuery query) {
		if(query.getReductionOption() == ReductionOption.VerifyTAPN)
			return getVerifyTAPN();
		else if(query.getReductionOption() == ReductionOption.VerifyTAPNdiscreteVerification)
			return getVerifyTAPNDiscreteVerification();
		else if(query.getReductionOption() == ReductionOption.VerifyPN || query.getReductionOption() == ReductionOption.VerifyPNApprox)
			return getVerifyPN();
		else
			return getVerifyta();
	}

	private VerificationOptions getVerificationOptionsFromQuery(pipe.dataLayer.TAPNQuery query) {
		if(query.getReductionOption() == ReductionOption.VerifyTAPN)
			return new VerifyTAPNOptions(query.getCapacity(), TraceOption.NONE, query.getSearchOption(), query.useSymmetry(), false, query.discreteInclusion(), query.inclusionPlaces());	// XXX DISABLES OverApprox
		else if(query.getReductionOption() == ReductionOption.VerifyTAPNdiscreteVerification)
			return new VerifyDTAPNOptions(query.getCapacity(), TraceOption.NONE, query.getSearchOption(), query.useSymmetry(), query.useGCD(), query.useTimeDarts(), query.usePTrie(), false,  query.discreteInclusion(), query.inclusionPlaces(), query.getWorkflowMode());
		else if(query.getReductionOption() == ReductionOption.VerifyPN || query.getReductionOption() == ReductionOption.VerifyPNApprox)
			return new VerifyPNOptions(query.getCapacity(), TraceOption.NONE, query.getSearchOption(), query.useOverApproximation());
		else
			return new VerifytaOptions(TraceOption.NONE, query.getSearchOption(), false, query.getReductionOption(), query.useSymmetry(), false);
	}
	
	private void MapQueryToNewNames(TAPNQuery query, NameMapping mapping) {
		RenameAllPlacesVisitor visitor = new RenameAllPlacesVisitor(mapping);
		query.getProperty().accept(visitor, null);
	}

	private Verifyta getVerifyta() {
		Verifyta verifyta = new Verifyta(new FileFinderImpl(), new MessengerImpl());
		verifyta.setup();
		return verifyta;
	}

	private static VerifyTAPN getVerifyTAPN() {
		VerifyTAPN verifytapn = new VerifyTAPN(new FileFinderImpl(), new MessengerImpl());
		verifytapn.setup();
		return verifytapn;
	}
	
	private static VerifyPN getVerifyPN() {
		VerifyPN verifypn = new VerifyPN(new FileFinderImpl(), new MessengerImpl());
		verifypn.setup();
		return verifypn;
	}
	
	private static VerifyTAPNDiscreteVerification getVerifyTAPNDiscreteVerification() {
		VerifyTAPNDiscreteVerification verifytapnDiscreteVerification = new VerifyTAPNDiscreteVerification(new FileFinderImpl(), new MessengerImpl());
		verifytapnDiscreteVerification.setup();
		return verifytapnDiscreteVerification;
	}
	
	private LoadedBatchProcessingModel loadModel(File modelFile) {
		fireStatusChanged("Loading model...");
		
		BatchProcessingModelLoader loader = new BatchProcessingModelLoader();
		try {
			return loader.load(modelFile);
		}
		catch(Exception e) {
			publishResult(modelFile.getName(), null, "Error loading model",	0, new NullStats());
			fireVerificationTaskComplete();
			return null;
		}
	}

	@Override
	protected void process(List<BatchProcessingVerificationResult> chunks) {
		for(BatchProcessingVerificationResult result : chunks){
			tableModel.addResult(result);
		}
	}
	
	@Override
	protected void done() {
		if(isCancelled()){
			if(modelChecker != null)
				modelChecker.kill();
		}
	}
	
	public void addBatchProcessingListener(BatchProcessingListener listener){
		Require.that(listener != null, "Listener cannot be null");
		listeners.add(listener);
	}

	public void removeBatchProcessingListener(BatchProcessingListener listener){
		Require.that(listener != null, "Listener cannot be null");
		listeners.remove(listener);
	}
	
	private void fireStatusChanged(String status) {
		for(BatchProcessingListener listener : listeners)
			listener.fireStatusChanged(new StatusChangedEvent(status));
	}
	
	private void fireFileChanged(String fileName) {
		for(BatchProcessingListener listener : listeners)
			listener.fireFileChanged(new FileChangedEvent(fileName));
	}
	
	private void fireVerificationTaskComplete() {
		verificationTasksCompleted++;
		for(BatchProcessingListener listener : listeners)
			listener.fireVerificationTaskComplete(new VerificationTaskCompleteEvent(verificationTasksCompleted));
	}
	
	private void fireVerificationTaskStarted() {
		for(BatchProcessingListener listener : listeners)
			listener.fireVerificationTaskStarted();
	}
}
