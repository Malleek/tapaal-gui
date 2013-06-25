package pipe.gui;

import java.awt.Container;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JOptionPane;
import pipe.dataLayer.DataLayer;
import pipe.dataLayer.Template;
import pipe.gui.graphicElements.Transition;
import pipe.gui.widgets.AnimationSelectmodeDialog;
import pipe.gui.widgets.EscapableDialog;
import pipe.gui.widgets.FileBrowser;
import dk.aau.cs.gui.TabContent;
import dk.aau.cs.gui.components.TransitionFireingComponent;
import dk.aau.cs.model.tapn.NetworkMarking;
import dk.aau.cs.model.tapn.TimeInterval;
import dk.aau.cs.model.tapn.TimedInputArc;
import dk.aau.cs.model.tapn.TimedPlace;
import dk.aau.cs.model.tapn.TimedToken;
import dk.aau.cs.model.tapn.TimedTransition;
import dk.aau.cs.model.tapn.TransportArc;
import dk.aau.cs.model.tapn.simulation.FiringMode;
import dk.aau.cs.model.tapn.simulation.OldestFiringMode;
import dk.aau.cs.model.tapn.simulation.RandomFiringMode;
import dk.aau.cs.model.tapn.simulation.TAPNNetworkTimeDelayStep;
import dk.aau.cs.model.tapn.simulation.TAPNNetworkTimedTransitionStep;
import dk.aau.cs.model.tapn.simulation.TAPNNetworkTrace;
import dk.aau.cs.model.tapn.simulation.TAPNNetworkTraceStep;
import dk.aau.cs.model.tapn.simulation.TimedTAPNNetworkTrace;
import dk.aau.cs.model.tapn.simulation.YoungestFiringMode;
import dk.aau.cs.util.IntervalOperations;
import dk.aau.cs.util.RequireException;
import dk.aau.cs.verification.VerifyTAPN.TraceType;

public class Animator {
	private ArrayList<TAPNNetworkTraceStep> actionHistory;
	private int currentAction;
	private ArrayList<NetworkMarking> markings;
	private int currentMarkingIndex = 0;
	private TAPNNetworkTrace trace = null;

	public FiringMode firingmode = new RandomFiringMode();
	private TabContent tab;
	private NetworkMarking initialMarking;

	private boolean isDisplayingUntimedTrace = false;

	public Animator() {
		actionHistory = new ArrayList<TAPNNetworkTraceStep>();
		currentAction = -1;
		markings = new ArrayList<NetworkMarking>();
	}

	public void setTabContent(TabContent tab) {
		this.tab = tab;
	}

	private NetworkMarking currentMarking() {
		return markings.get(currentMarkingIndex);
	}

	public void SetTrace(TAPNNetworkTrace trace) {
		if (trace.isConcreteTrace()) {
			this.trace = trace;
			setTimedTrace(trace);
		} else {
			setUntimedTrace(trace);
			isDisplayingUntimedTrace = true;
		}
		currentAction = -1;
		currentMarkingIndex = 0;
		CreateGui.getAnimationHistory().setSelectedIndex(0);
		CreateGui.getAnimationController().setAnimationButtonsEnabled();
	}

	private void setUntimedTrace(TAPNNetworkTrace trace) {
		tab.addAbstractAnimationPane();
		AnimationHistoryComponent untimedAnimationHistory = CreateGui.getAbstractAnimationPane();

		for(TAPNNetworkTraceStep step : trace){
			untimedAnimationHistory.addHistoryItem(step.toString());
		}

		CreateGui.getAbstractAnimationPane().setSelectedIndex(0);
		setFiringmode("Random");

		JOptionPane.showMessageDialog(CreateGui.getApp(),
				"The verification process returned an untimed trace.\n\n"
						+ "This means that with appropriate time delays the displayed\n"
						+ "sequence of discrete transitions can become a concrete trace.\n"
						+ "In case of liveness properties (EG, AF) the untimed trace\n"
						+ "either ends in a deadlock, a time divergent computation without\n"
						+ "any discrete transitions, or it loops back to some earlier configuration.\n"
						+ "The user may experiment in the simulator with different time delays\n"
						+ "in order to realize the suggested untimed trace in the model.",
						"Verification Information", JOptionPane.INFORMATION_MESSAGE);
	}

	private void setTimedTrace(TAPNNetworkTrace trace) {
		for (TAPNNetworkTraceStep step : trace) {
			addMarking(step, step.performStepFrom(currentMarking()));
		}
		CreateGui.getAnimationHistory().setLastShown(getTrace().getTraceType());
	}

	private void addToTimedTrace(List<TAPNNetworkTraceStep> stepList){
		for (TAPNNetworkTraceStep step : stepList) {
			addMarking(step, step.performStepFrom(currentMarking()));
		}
	}

	public NetworkMarking getInitialMarking(){
		return initialMarking;
	}

	public NetworkMarking getLastMarking(){
		return markings.get(markings.size()-1);
	}
	
	public NetworkMarking getCurrentMarking(){
		return markings.get(currentMarkingIndex);
	}
	
	/**
	 * Highlights enabled transitions
	 */
	public void highlightEnabledTransitions() {
		updateFireableTransitions();
		DataLayer current = activeGuiModel();

		Iterator<Transition> transitionIterator = current.returnTransitions();
		while (transitionIterator.hasNext()) {
			Transition tempTransition = transitionIterator.next();
			if (tempTransition.isEnabled(true) || tempTransition.isBlueTransition(true)) {
				current.notifyObservers();
				tempTransition.repaint();
			}
		}
	}

	/**
	 * Called during animation to unhighlight previously highlighted transitions
	 */
	public void unhighlightDisabledTransitions() {
		DataLayer current = activeGuiModel();

		Iterator<Transition> transitionIterator = current.returnTransitions();
		while (transitionIterator.hasNext()) {
			Transition tempTransition = transitionIterator.next();
			if (!(tempTransition.isEnabled(true)) || !tempTransition.isBlueTransition(true)) {
				current.notifyObservers();
				tempTransition.repaint();
			}
		}
	}

	public void updateFireableTransitions(){
		TransitionFireingComponent transFireComponent = CreateGui.getTransitionFireingComponent();
		transFireComponent.startReInit();

		for( Template temp : CreateGui.getCurrentTab().activeTemplates()){
			Iterator<Transition> transitionIterator = temp.guiModel().returnTransitions();
			while (transitionIterator.hasNext()) {
				Transition tempTransition = transitionIterator.next();
				if (tempTransition.isEnabled(true) || (tempTransition.isBlueTransition(true) && CreateGui.getApp().isShowingBlueTransitions())) {
					transFireComponent.addTransition(temp, tempTransition);
				}
			}
		}

		transFireComponent.reInitDone();
	}

	/**
	 * Called at end of animation and resets all Transitions to false and
	 * unhighlighted
	 */
	private void disableTransitions() {
		for(Template template : tab.allTemplates())
		{
			Iterator<Transition> transitionIterator = template.guiModel().returnTransitions();
			while (transitionIterator.hasNext()) {
				Transition tempTransition = transitionIterator.next();
				tempTransition.setEnabledFalse();
				tempTransition.setBlueTransitionFalse();
				activeGuiModel().notifyObservers();
				tempTransition.repaint();
			}
		}
	}

	/**
	 * Stores model at start of animation
	 */
	public void storeModel() {
		initialMarking = tab.network().marking();
		resethistory();
		markings.add(initialMarking);
	}

	/**
	 * Restores model at end of animation and sets all transitions to false and
	 * unhighlighted
	 */
	public void restoreModel() {
		disableTransitions();
		tab.network().setMarking(initialMarking);
		currentAction = -1;
	}

	/**
	 * Steps back through previously fired transitions
	 * 
	 * @author jokke refactored and added backwards firing for TAPNTransitions
	 */

	public void stepBack() {
		if (!actionHistory.isEmpty()){
			TAPNNetworkTraceStep lastStep = actionHistory.get(currentAction);
			if(isDisplayingUntimedTrace && lastStep instanceof TAPNNetworkTimedTransitionStep){
				AnimationHistoryComponent untimedAnimationHistory = tab.getUntimedAnimationHistory();
				String previousInUntimedTrace = untimedAnimationHistory.getElement(untimedAnimationHistory.getSelectedIndex());
				if(previousInUntimedTrace.equals(lastStep.toString())){
					untimedAnimationHistory.stepBackwards();
				}
			}

			tab.network().setMarking(markings.get(currentMarkingIndex - 1));

			activeGuiModel().repaintPlaces();
			unhighlightDisabledTransitions();
			highlightEnabledTransitions();
			currentAction--;
			currentMarkingIndex--;
			reportBlockingPlaces();
		}
	}

	/**
	 * Steps forward through previously fired transitions
	 */

	public void stepForward() {
		if(currentAction == actionHistory.size()-1 && trace != null){
			int selectedIndex = CreateGui.getAnimationHistory().getSelectedIndex();
			int action = currentAction;
			int markingIndex = currentMarkingIndex;

			if(getTrace().getTraceType() == TraceType.EG_DELAY_FOREVER){
				addMarking(new TAPNNetworkTimeDelayStep(BigDecimal.ONE), currentMarking().delay(BigDecimal.ONE));
			}
			if(getTrace().getLoopToIndex() != -1){
				addToTimedTrace(getTrace().getLoopSteps());
			}

			CreateGui.getAnimationHistory().setSelectedIndex(selectedIndex);
			currentAction = action;
			currentMarkingIndex = markingIndex;
		}

		if (currentAction < actionHistory.size() - 1) {
			TAPNNetworkTraceStep nextStep = actionHistory.get(currentAction+1);
			if(isDisplayingUntimedTrace && nextStep instanceof TAPNNetworkTimedTransitionStep){
				AnimationHistoryComponent untimedAnimationHistory = tab.getUntimedAnimationHistory();
				String nextInUntimedTrace = untimedAnimationHistory.getElement(untimedAnimationHistory.getSelectedIndex()+1);
				if(nextInUntimedTrace.equals(nextStep.toString())){
					untimedAnimationHistory.stepForward();
				}
			}

			tab.network().setMarking(markings.get(currentMarkingIndex + 1));

			activeGuiModel().repaintPlaces();
			unhighlightDisabledTransitions();
			highlightEnabledTransitions();
			currentAction++;
			currentMarkingIndex++;
			activeGuiModel().redrawVisibleTokenLists();
			reportBlockingPlaces();

		}
	}
	
	public void dFireTransition(TimedTransition transition){
		if(!CreateGui.getApp().isShowingBlueTransitions()){
			fireTransition(transition);
			return;
		}
		
		TimeInterval dInterval = transition.getdInterval();
		
		BigDecimal delayGranularity = CreateGui.getCurrentTab().getBlueTransitionControl().getValue();
		//Make sure the granularity is small enough
		BigDecimal lowerBound = IntervalOperations.getRatBound(dInterval.lowerBound()).getBound();
		if(!dInterval.IsLowerBoundNonStrict() && !dInterval.isIncluded(lowerBound.add(delayGranularity))){
			do{
				delayGranularity = delayGranularity.divide(BigDecimal.TEN);
			} while (delayGranularity.compareTo(new BigDecimal("0.00001")) >= 0 && !dInterval.isIncluded(lowerBound.add(delayGranularity)));
		}
		
		if(delayGranularity.compareTo(new BigDecimal("0.00001")) < 0){
			JOptionPane.showMessageDialog(CreateGui.getApp(), "<html>Due to the limit of only five decimal points in the simulator</br> its not possible to fire the transition</html>");
		} else {
			BigDecimal delay = CreateGui.getCurrentTab().getBlueTransitionControl().getDelayMode().GetDelay(transition, dInterval, delayGranularity);
			if(delay != null){
				if(delay.compareTo(BigDecimal.ZERO) != 0){ //Don't delay if the chosen delay is 0
					if(!letTimePass(delay)){
						return;
					}
				}
			
				fireTransition(transition);
			}
		}
	}

	// TODO: Clean up this method
	private void fireTransition(TimedTransition transition) {

		if(!clearStepsForward()){
			return;
		}

		NetworkMarking next = null;
		try{
			if (getFiringmode() != null) {
				next = currentMarking().fireTransition(transition, getFiringmode());
			} else {
				List<TimedToken> tokensToConsume = getTokensToConsume(transition);
				if(tokensToConsume == null) return; // Cancelled
				next = currentMarking().fireTransition(transition, tokensToConsume);
			}
		}catch(RequireException e){
			JOptionPane.showMessageDialog(CreateGui.getApp(), "There was an error firing the transition. Reason: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		// It is important that this comes after the above, since 
		// cancelling the token selection dialogue above should not result in changes 
		// to the untimed animation history
		if (isDisplayingUntimedTrace){
			AnimationHistoryComponent untimedAnimationHistory = tab.getUntimedAnimationHistory();
			if(untimedAnimationHistory.isStepForwardAllowed()){
				String nextFromUntimedTrace = untimedAnimationHistory.getElement(untimedAnimationHistory.getSelectedIndex()+1);

				if(nextFromUntimedTrace.equals(transition.model().name() + "." + transition.name()) || transition.isShared() && nextFromUntimedTrace.equals(transition.name())){
					untimedAnimationHistory.stepForward();
				}else{
					int fireTransition = JOptionPane.showConfirmDialog(CreateGui.getApp(),
							"Are you sure you want to fire a transition which does not follow the untimed trace?\n"
									+ "Firing this transition will discard the untimed trace and revert to standard simulation.",
									"Discrading Untimed Trace", JOptionPane.YES_NO_OPTION );

					if (fireTransition == JOptionPane.NO_OPTION){
						return;
					}else{
						removeSetTrace(false);
					}
				}
			}
		}

		tab.network().setMarking(next);
		addMarking(new TAPNNetworkTimedTransitionStep(transition, null), next);
		
		activeGuiModel().repaintPlaces();
		highlightEnabledTransitions();
		unhighlightDisabledTransitions();
		
		reportBlockingPlaces();

	}

	public boolean letTimePass(BigDecimal delay) {

		if(!clearStepsForward()){
			return false;
		}

		boolean result = false;
		if (currentMarking().isDelayPossible(delay)) {
			NetworkMarking delayedMarking = currentMarking().delay(delay);
			tab.network().setMarking(delayedMarking);
			addMarking(new TAPNNetworkTimeDelayStep(delay), delayedMarking);
			result = true;
		}

		activeGuiModel().repaintPlaces();
		highlightEnabledTransitions();
		unhighlightDisabledTransitions();
		reportBlockingPlaces();
		return result;
	}

	public void reportBlockingPlaces(){

		try{
			BigDecimal delay = CreateGui.getAnimationController().getCurrentDelay();
			if(delay.compareTo(new BigDecimal(0))<=0){
				CreateGui.getAnimationController().getOkButton().setEnabled(false);
				CreateGui.getAnimationController().getOkButton().setToolTipText("Time delay is possible only for positive rational numbers");
			} else {
				List<TimedPlace> blockingPlaces = currentMarking().getBlockingPlaces(delay);
				if(blockingPlaces.size() == 0){
					CreateGui.getAnimationController().getOkButton().setEnabled(true);
					CreateGui.getAnimationController().getOkButton().setToolTipText("Press to add the delay");
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append("<html>Time delay of " + delay + " time unit(s) is disabled due to <br /> age invariants in the following places:<br /><br />");
					for (TimedPlace t :blockingPlaces){
						sb.append(t.toString() + "<br />");
					}
					//JOptionPane.showMessageDialog(null, sb.toString());
					sb.append("</html>");
					CreateGui.getAnimationController().getOkButton().setEnabled(false);
					CreateGui.getAnimationController().getOkButton().setToolTipText(sb.toString());
				}
			}
		} catch (NumberFormatException e) {
			// Do nothing, invalud number
		} catch (ParseException e) {
			CreateGui.getAnimationController().getOkButton().setEnabled(false);
			CreateGui.getAnimationController().getOkButton().setToolTipText("The text in the input field is not a number");
		}
	}

	private DataLayer activeGuiModel() {
		return tab.currentTemplate().guiModel();
	}

	public void resethistory() {
		actionHistory.clear();
		markings.clear();
		currentAction = -1;
		currentMarkingIndex = 0;
		tab.getAnimationHistory().reset();
		if(tab.getUntimedAnimationHistory() != null){
			tab.getUntimedAnimationHistory().reset();
		}
	}

	public FiringMode getFiringmode() {
		return firingmode;
	}

	// removes stored markings and actions from index "startWith" (included)
	private void removeStoredActions(int startWith) {
		int lastIndex = actionHistory.size() - 1;
		for (int i = startWith; i <= lastIndex; i++) {
			removeLastHistoryStep();
		}
	}

	private void addMarking(TAPNNetworkTraceStep action, NetworkMarking marking) {
		if (currentAction < actionHistory.size() - 1)
			removeStoredActions(currentAction + 1);

		CreateGui.getAnimationHistory().addHistoryItem(action.toString());
		actionHistory.add(action);
		markings.add(marking);
		currentAction++;
		currentMarkingIndex++;
	}

	private void removeLastHistoryStep() {
		actionHistory.remove(actionHistory.size() - 1);
		markings.remove(markings.size() - 1);
	}

	public void setFiringmode(String t) {
		if (t.equals("Random")) {
			firingmode = new RandomFiringMode();
		} else if (t.equals("Youngest")) {
			firingmode = new YoungestFiringMode();
		} else if (t.equals("Oldest")) {
			firingmode = new OldestFiringMode();
		} else if (t.equals("Manual")) {
			firingmode = null;
		} else {
			System.err
			.println("Illegal firing mode mode: " + t + " not found.");
		}

		CreateGui.getAnimationController().updateFiringModeComboBox();
		CreateGui.getAnimationController().setToolTipText("Select a method for choosing tokens during transition firing");
	}	

	enum FillListStatus{
		lessThanWeight,
		weight,
		moreThanWeight
	}

	//Creates a list of tokens if there is only weight tokens in each of the places
	//Used by getTokensToConsume
	private  FillListStatus fillList(TimedTransition transition, List<TimedToken> listToFill){
		for(TimedInputArc in: transition.getInputArcs()){
			List<TimedToken> elligibleTokens = in.getElligibleTokens();
			if(elligibleTokens.size() < in.getWeight().value()){
				return FillListStatus.lessThanWeight;
			} else if(elligibleTokens.size() == in.getWeight().value()){
				listToFill.addAll(elligibleTokens);
			} else {
				return FillListStatus.moreThanWeight;
			}
		}
		for(TransportArc in: transition.getTransportArcsGoingThrough()){
			List<TimedToken> elligibleTokens = in.getElligibleTokens();
			if(elligibleTokens.size() < in.getWeight().value()){
				return FillListStatus.lessThanWeight;
			} else if(elligibleTokens.size() == in.getWeight().value()){
				listToFill.addAll(elligibleTokens);
			} else {
				return FillListStatus.moreThanWeight;
			}
		}
		return FillListStatus.weight;
	}

	private List<TimedToken> getTokensToConsume(TimedTransition transition){
		//If there are only "weight tokens in each place
		List<TimedToken> result = new ArrayList<TimedToken>();
		boolean userShouldChoose = false;
		if(transition.isShared()){
			for(TimedTransition t : transition.sharedTransition().transitions()){
				FillListStatus status = fillList(t, result);
				if(status == FillListStatus.lessThanWeight){
					return null;
				} else if(status == FillListStatus.moreThanWeight){
					userShouldChoose = true;
					break;
				}
			}
		} else {
			FillListStatus status = fillList(transition, result);
			if(status == FillListStatus.lessThanWeight){
				return null;
			} else if(status == FillListStatus.moreThanWeight){
				userShouldChoose = true;
			}
		}

		if (userShouldChoose){
			return showSelectSimulatorDialogue(transition);
		} else {
			return result;
		}
	}

	public List<TimedToken> showSelectSimulatorDialogue(TimedTransition transition) {
		EscapableDialog guiDialog = new EscapableDialog(CreateGui.getApp(), "Select Tokens", true);

		Container contentPane = guiDialog.getContentPane();
		contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
		AnimationSelectmodeDialog animationSelectmodeDialog = new AnimationSelectmodeDialog(transition);
		contentPane.add(animationSelectmodeDialog);
		guiDialog.setResizable(true);

		// Make window fit contents' preferred size
		guiDialog.pack();

		// Move window to the middle of the screen
		guiDialog.setLocationRelativeTo(null);
		guiDialog.setVisible(true);

		return animationSelectmodeDialog.getTokens();
	}

	public void reset(){
		resethistory();
		removeSetTrace(false);
	}

	public boolean removeSetTrace(boolean askUser){
		if(askUser && isShowingTrace()){ //Warn about deleting trace
			int answer = JOptionPane.showConfirmDialog(CreateGui.getApp(), 
					"You are about to remove the current trace.", 
					"Removing Trace", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if(answer != JOptionPane.OK_OPTION) return false;
		}
		if(isDisplayingUntimedTrace){
			CreateGui.removeAbstractAnimationPane();
		}
		isDisplayingUntimedTrace = false;
		trace = null;
		return true;
	}

	public TimedTAPNNetworkTrace getTrace(){
		return (TimedTAPNNetworkTrace)trace;
	}

	private boolean clearStepsForward(){
		boolean answer = true;
		if(!isDisplayingUntimedTrace){
			answer = removeSetTrace(true);
		}
		if(answer){
			CreateGui.getAnimationHistory().clearStepsForward();
		}
		return answer;
	}

	public boolean isShowingTrace(){
		return isDisplayingUntimedTrace || trace != null;
	}
	
	public void exportTrace(){
		TimedTAPNNetworkTrace trace = getTrace();
		StringBuilder output = new StringBuilder();
		output.append("Initial Marking\n");
		try{
			for(TAPNNetworkTraceStep step : trace){
				output.append(step.toString() + "\n");
			}
			FileBrowser fb = new FileBrowser("Export trace","txt");
			String path = fb.saveFile("trace.txt");
			FileWriter fw = new FileWriter(path);
			fw.write(output.substring(0,  output.length()-1));
			fw.close();
		} catch (NullPointerException e) {
			JOptionPane.showMessageDialog(CreateGui.getApp(), "Trace is empty.", "Error", JOptionPane.ERROR_MESSAGE);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(CreateGui.getApp(), "Error exporting trace.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}
