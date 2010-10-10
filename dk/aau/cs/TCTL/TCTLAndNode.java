package dk.aau.cs.TCTL;

import dk.aau.cs.TCTL.visitors.ITCTLVisitor;


public class TCTLAndNode extends TCTLAbstractStateProperty {

	private TCTLAbstractStateProperty property1;
	private TCTLAbstractStateProperty property2;
	
	public void setProperty1(TCTLAbstractStateProperty property1) {
		this.property1 = property1;
		this.property1.setParent(this);
	}

	public TCTLAbstractStateProperty getProperty1() {
		return property1;
	}

	public void setProperty2(TCTLAbstractStateProperty property2) {
		this.property2 = property2;
		this.property2.setParent(this);
	}

	public TCTLAbstractStateProperty getProperty2() {
		return property2;
	}
	
	public TCTLAndNode(TCTLAbstractStateProperty property1, TCTLAbstractStateProperty property2) {
		this.property1 = property1;
		this.property1.setParent(this);
		this.property2 = property2;
		this.property2.setParent(this);
	}
	public TCTLAndNode(TCTLAbstractStateProperty property1) {
		this.property1 = property1;
		this.property1.setParent(this);
		this.property2 = new TCTLStatePlaceHolder();
		this.property2.setParent(this);
	}
	

	@Override
	public boolean isSimpleProperty() {
		return false;
	}
	
	@Override
	public String toString() {
		String s1 = property1.isSimpleProperty() ? property1.toString()
				                         : "(" + property1.toString() + ")";
		String s2 = property2.isSimpleProperty() ? property2.toString()
                                         : "(" + property2.toString() + ")";
		return s1 + " and " + s2;
	}
	
	@Override
	public StringPosition[] getChildren() {
		int start1 = property1.isSimpleProperty() ? 0 : 1;
		int end1 = start1 + property1.toString().length();
		StringPosition position1 = new StringPosition(start1, end1, property1);
		
		int start2 = end1 + 5 + (property1.isSimpleProperty() ? 0 : 1)
		                      + (property2.isSimpleProperty() ? 0 : 1);
		int end2 = start2 + property2.toString().length();
		StringPosition position2 = new StringPosition(start2, end2, property2);
		
		StringPosition[] children = {position1, position2};
		return children;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof TCTLAndNode) {
			TCTLAndNode node = (TCTLAndNode)o;
			return property1.equals(node.property1) && property2.equals(node.property2);
		}
		return false;
	}

	@Override
	public TCTLAbstractStateProperty copy() {
		return new TCTLAndNode(property1.copy(), property2.copy());
	}

	@Override
	public TCTLAbstractStateProperty replace(TCTLAbstractProperty object1, TCTLAbstractProperty object2) {
		if (this == object1 && object2 instanceof TCTLAbstractStateProperty) {
			TCTLAbstractStateProperty obj2 = (TCTLAbstractStateProperty)object2;
			obj2.setParent(this.parent);
			return obj2;
		} else {
			property1 = property1.replace(object1, object2);
			property2 = property2.replace(object1, object2);
			return this;
		}
	}
	
	@Override
	public void accept(ITCTLVisitor visitor) {
		visitor.visit(this);
		
	}

	@Override
	public boolean containsPlaceHolder() {
		return property1.containsPlaceHolder() || property2.containsPlaceHolder();
	}
	
	@Override
	public boolean containsAtomicPropWithSpecificPlace(String placeName) {
			return property1.containsAtomicPropWithSpecificPlace(placeName) || property2.containsAtomicPropWithSpecificPlace(placeName);
	}

}
