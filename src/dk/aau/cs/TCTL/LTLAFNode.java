package dk.aau.cs.TCTL;

public class LTLAFNode extends TCTLAFNode {
    TCTLAbstractStateProperty property;

    public void setProperty(TCTLAbstractStateProperty property) {
        this.property = property;
        this.property.setParent(this);
    }

    public TCTLAbstractStateProperty getProperty() {
        return property;
    }

    public LTLAFNode(TCTLAbstractStateProperty property) {
        this.property = property;
        this.property.setParent(this);
    }

    public LTLAFNode() {
        this.property = new TCTLStatePlaceHolder();
        this.property.setParent(this);
    }

    @Override
    public String toString() {
        String s = property.isSimpleProperty() ? property.toString() : "("
            + property.toString() + ")";
        return "F " + s;
    }

    @Override
    public StringPosition[] getChildren() {
        int start = property.isSimpleProperty() ? 0 : 1;
        start = start + 2;
        int end = start + property.toString().length();
        StringPosition position = new StringPosition(start, end, property);

        StringPosition[] children = { position };
        return children;
    }

    @Override
    public TCTLAbstractPathProperty replace(TCTLAbstractProperty object1,
                                            TCTLAbstractProperty object2) {
        if (this == object1 && object2 instanceof TCTLAbstractPathProperty) {
            return (TCTLAbstractPathProperty) object2;
        } else {
            property = property.replace(object1, object2);
            return this;
        }
    }

    @Override
    public boolean containsPlaceHolder() {
        return property.containsPlaceHolder();
    }

    @Override
    public void convertForReducedNet(String templateName) {
        property.convertForReducedNet(templateName);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LTLAFNode) {
            LTLAFNode node = (LTLAFNode) o;
            return property.equals(node.getProperty());
        }
        return false;
    }

    @Override
    public TCTLAbstractPathProperty copy() {
        return new LTLAFNode(property.copy());
    }

    @Override
    public boolean hasNestedPathQuantifiers() {
        return property instanceof TCTLPathToStateConverter || property.hasNestedPathQuantifiers();
    }

    public boolean containsAtomicPropositionWithSpecificPlaceInTemplate(String templateName, String placeName) {
        return property.containsAtomicPropositionWithSpecificPlaceInTemplate(templateName, placeName);
    }

    public boolean containsAtomicPropositionWithSpecificTransitionInTemplate(String templateName, String transitionName) {
        return property.containsAtomicPropositionWithSpecificTransitionInTemplate(templateName, transitionName);
    }

    @Override
    public TCTLAbstractProperty findFirstPlaceHolder() {
        return property.findFirstPlaceHolder();
    }
}
