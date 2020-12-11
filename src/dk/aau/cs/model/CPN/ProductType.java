package dk.aau.cs.model.CPN;

import java.util.HashMap;
import java.util.Vector;

public class ProductType extends ColorType {

    private Vector<ColorType> constituents = new Vector<ColorType>();
    private String name;
    private String id;
    private HashMap<Vector<Color>, Color> colorCache = new HashMap<Vector<Color>, Color>();

    @Override
    public Integer size() {
        return constituents.size();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProductType))
            return false;
        ProductType object = (ProductType) o;

        if (!object.name.equals(this.name))
            return false;

        if(!object.size().equals(size())){
            return false;
        }
        for(int i = 0; i < constituents.size(); i++){
            if(!constituents.get(i).equals(object.constituents.get(i))){
                return false;
            }
        }
        return true;
    }

    public ProductType(String name) {
        super(name);
        this.name = name;
    }
    public ProductType(String name, String id) {
        super(name, id);
        this.name = name;
        this.id = id;
    }
    public boolean contains(ColorType colorType){
        for (ColorType ct : constituents){
            if(ct.equals(colorType)){
                return true;
            }
        }
        return false;
    }
    public Vector<ColorType> getColorTypes() {return constituents; }

    public void addType(ColorType colortype) {
        constituents.add(colortype);
    }

    //Adding colors to product-types no longer makes sense.
    public void addColor(String colorName) {
        assert(false);
    }

    public String toString() {
        String out = "Domain: " + name + " is <";
        for (ColorType element : constituents) {
            out += element.getName() + ", ";
        }
        out = out.substring(0, out.length() -2);
        out += ">";
        return out;
    }

    private int getConstituentCombinationSize(){
        int result = 1;
        for (ColorType ct : constituents) {
            result *= ct.size();
        }
        return result;
    }

    @Override
    public boolean contains(Color color){
        Vector<Color> tupleColors = color.getTuple();
        if(tupleColors != null) {
            if(constituents.size() == tupleColors.size()) {
                //Are the colors in a tuple color ordered correctly?
                //maybe do more coarse check
                for(int i = 0; i < constituents.size(); i++) {
                    if(!constituents.elementAt(i).contains(tupleColors.elementAt(i))){
                        return false;
                    }
                }
                return true;
            }
        } else {
            for(ColorType ct : constituents) {
                if (ct.contains(color)) {
                    return true;
                }
            }
        }
        return false;
    }


    @Override
    public Vector<Color> getColors(){
        Vector<Color> colors = new Vector<>();

        if (getConstituentCombinationSize() != colorCache.size()) {

            Vector<Vector<Color>> tupleColors = new Vector<>();
            for (ColorType ct : constituents) {
                if (tupleColors.isEmpty()) {
                    for (Color color : ct.getColors()) {
                        Vector<Color> tupleColor = new Vector<>();
                        tupleColor.add(color);
                        tupleColors.add(tupleColor);
                    }
                } else {
                    Vector<Vector<Color>> newTupleColors = new Vector<>();
                    for (Color color : ct.getColors()) {
                        Vector<Vector<Color>> tupleColorsClone = (Vector<Vector<Color>>) tupleColors.clone();
                        for (Vector<Color> tupleColor : tupleColorsClone) {
                            tupleColor.add(color);
                        }
                        newTupleColors.addAll(tupleColorsClone);
                    }
                    tupleColors = newTupleColors;
                }

            }

            for (Vector<Color> tupleColor : tupleColors) {
                colorCache.putIfAbsent(tupleColor, new Color(this, 0, tupleColor));
            }
        }

        for (Color cachedColor : colorCache.values()){
            colors.add(cachedColor);
        }

        return colors;
    }

    public boolean containsTypes(Vector<ColorType> colorTypes) {
        return constituents.equals(colorTypes);
    }

    public Color getColor(Vector<Color> colors) {
        Color result = colorCache.get(colors);
        if (result == null) {
            //TODO: Figure out id
            result = new Color(this, 0, colors);
            colorCache.put(colors, result);
        }
        return result;
    }

    @Override
    public Color getFirstColor() {
        Vector<Color> colors = new Vector<Color>();
        for (ColorType ct : constituents) {
            colors.add(ct.getFirstColor());
        }
        return getColor(colors);
    }

    public Vector<ColorType> getConstituents(){
        return constituents;
    }
    public void setConstituents(Vector<ColorType> constituents) {
        this.constituents = constituents;
    }

    public void replaceColorType(ColorType newColorType, ColorType oldColorType){
        for (ColorType ct : constituents){
            if(ct.equals(oldColorType)){
                int index = constituents.indexOf(ct);
                constituents.set(index, newColorType);
            }
        }
/*        for(ColorType ct : constituents){
            if(ct instanceof ProductType){
                ((ProductType)ct).replaceColorType(newColorType,oldColorType);
            }
        }*/
    }
}