package pipe.gui.handler;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import pipe.dataLayer.Arc;
import pipe.dataLayer.Place;
import pipe.dataLayer.TransportArcComponent;
import pipe.gui.action.SplitArcAction;

public class TransportArcHandler extends TimedArcHandler {

	public TransportArcHandler(Container contentpane, Arc obj) {
		super(contentpane, obj);
		enablePopup = true;
	}

	@Override
	public JPopupMenu getPopup(MouseEvent e) {
		int popupIndex = 0;
		JMenuItem menuItem;
		JPopupMenu popup = super.getPopup(e);

		if (myObject instanceof TransportArcComponent) {
			// if ( ! ( ((TimedArc) myObject).getSource() instanceof Transition)
			// ){
			final TransportArcComponent tarc = (TransportArcComponent) myObject;

			menuItem = new JMenuItem("Properties");
			menuItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (tarc.getSource() instanceof Place) {
						tarc.showTimeIntervalEditor();
					} else {
						tarc.getConnectedTo().showTimeIntervalEditor();
					}
				}
			});
			popup.insert(menuItem, popupIndex++);
			// }
			// menuItem = new JMenuItem(new EditGroupAction(contentPane,
			// (TransportArc)myObject));
			// menuItem.setText("Edit Grouping");
			// popup.insert(menuItem, popupIndex++);

			menuItem = new JMenuItem(new SplitArcAction((Arc) myObject, e
					.getPoint()));
			menuItem.setText("Insert Point");
			popup.insert(menuItem, popupIndex++);

			popup.insert(new JPopupMenu.Separator(), popupIndex);
		}
		return popup;
	}
}