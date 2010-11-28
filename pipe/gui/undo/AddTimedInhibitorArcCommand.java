package pipe.gui.undo;

import pipe.dataLayer.DataLayer;
import pipe.dataLayer.TimedInhibitorArcComponent;
import pipe.gui.DrawingSurfaceImpl;
import dk.aau.cs.model.tapn.TimedArcPetriNet;

public class AddTimedInhibitorArcCommand extends AddTAPNElementCommand {
	private final TimedInhibitorArcComponent inhibitorArc;

	public AddTimedInhibitorArcCommand(TimedInhibitorArcComponent inhibitorArc, TimedArcPetriNet tapn,
			DataLayer guiModel, DrawingSurfaceImpl view) {
		super(tapn, guiModel, view);
		this.inhibitorArc = inhibitorArc;
	}

	@Override
	public void undo() {
		inhibitorArc.delete();
		view.repaint();
	}
	
	@Override
	public void redo() {
		inhibitorArc.undelete(view);
		tapn.add(inhibitorArc.underlyingTimedInhibitorArc());
		view.repaint();
	}

}
