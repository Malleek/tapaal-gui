package dk.aau.cs.model.CPN.Expressions;

import dk.aau.cs.model.CPN.Color;
import dk.aau.cs.model.CPN.ColorMultiset;
import dk.aau.cs.model.CPN.ColorType;
import dk.aau.cs.model.CPN.ExpressionSupport.ExprStringPosition;
import dk.aau.cs.model.CPN.ExpressionSupport.ExprValues;
import dk.aau.cs.model.CPN.Variable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class AddExpression extends ArcExpression {

    private Vector<ArcExpression> constituents;

    public AddExpression(Vector<ArcExpression> constituents) {
        this.constituents = constituents;
    }

    public AddExpression(AddExpression otherExpr)  {
        super(otherExpr);
        this.constituents = new Vector<>(otherExpr.constituents);
    }


    public Vector<ArcExpression> getAddExpression (){return constituents;}

    public ColorMultiset eval(ExpressionContext context) {
        ColorMultiset result = null;
        //Start with null, to use colortype of first constituent
        for (ArcExpression constituent : constituents) {
            if (result == null) {
                System.out.println(constituent);
                result = constituent.eval(context);
            } else {
                ColorMultiset cm = constituent.eval(context);
                result.addAll(cm);
            }
        }
        assert(result != null);
        return result;
    }

    public void expressionType() {

    }

    public Integer weight() {
        Integer res = 0;
        for (ArcExpression element : constituents) {
            res += element.weight();
        }
        return res;
    }

    @Override
    public ArcExpression removeColorFromExpression(Color color, ColorType newColorType) {
        List<ArcExpression> toRemove = new ArrayList<>();
        for(ArcExpression expr : constituents){
            if(expr.removeColorFromExpression(color, newColorType) == null){
                toRemove.add(expr);
            }
        }
        for(ArcExpression expr : toRemove){
            constituents.remove(expr);
        }
        if(constituents.size() < 1){
            return null;
        } else if(constituents.size() < 2){
            return constituents.get(0);
        } else{
            return this;
        }
    }

    @Override
    public ArcExpression removeExpressionVariables(List<Variable> variables) {
        Vector<ArcExpression> newConstituents = new Vector<>();
        for(ArcExpression expr : constituents) {
            ArcExpression newExpr = expr.removeExpressionVariables(variables);
            if(newExpr != null) {
                newConstituents.add(newExpr);
            }
        }

        if(newConstituents.isEmpty()) {
            return null;
        } else {
            return new AddExpression(newConstituents);
        }
    }

    @Override
    public ArcExpression replace(Expression object1, Expression object2){
        return replace(object1,object2,false);
    }
    @Override
    public ArcExpression replace(Expression object1, Expression object2, boolean replaceAllInstances) {
        if (object1 == this && object2 instanceof ArcExpression) {
            ArcExpression obj2 = (ArcExpression) object2;
            obj2.setParent(parent);
            return obj2;
        } else {
            for (int i = 0; i < constituents.size(); i++) {
                constituents.set(i, constituents.get(i).replace(object1, object2, replaceAllInstances));
            }
            return this;
        }
    }

    @Override
    public ArcExpression copy() {
        return new AddExpression(constituents);
    }

    @Override
    public ArcExpression deepCopy() {
        Vector<ArcExpression> constituentsCopy = new Vector<>();
        for (ArcExpression expr : constituents) {
            constituentsCopy.add(expr.deepCopy());
        }
        return new AddExpression(constituentsCopy);
    }

    @Override
    public boolean containsPlaceHolder() {
        for (int i = 0; i <constituents.size(); i++) {
            if (constituents.get(i).containsPlaceHolder()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ArcExpression findFirstPlaceHolder() {
        for (int i = 0; i <constituents.size(); i++) {
            if (constituents.get(i).containsPlaceHolder()) {
                return constituents.get(i).findFirstPlaceHolder();
            }
        }
        return null;
    }

    @Override
    public void getValues(ExprValues exprValues) {
        for (ArcExpression constituent : constituents) {
            constituent.getValues(exprValues);
        }
    }

    @Override
    public boolean isSimpleProperty() {return false; }

    @Override
    public ExprStringPosition[] getChildren() {
        ExprStringPosition[] children = new ExprStringPosition[constituents.size()];
        int i = 0;
        int endPrev = 0;
        boolean wasPrevSimple = false;
        for (ArcExpression p : constituents) {

            int start = 1;
            int end = 0;

            if (i == 0) {
                end = start + p.toString().length();
                endPrev = end;
            } else {
                start = endPrev + 3;
                end = start + p.toString().length();

                endPrev = end;
            }

            ExprStringPosition pos = new ExprStringPosition(start, end, p);

            children[i] = pos;
            i++;
        }

        return children;



    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AddExpression) {
            AddExpression expr = (AddExpression) o;
            return constituents.equals(expr.constituents);
        }
        return false;
    }

    public void getVariables(Set<Variable> variables) {
        for (ArcExpression element : constituents) {
            element.getVariables(variables);
        }
    }

    public String toString() {
        String res = "(" + constituents.get(0).toString();
        for (int i = 1; i < constituents.size(); ++i) {
            res += " + " + constituents.get(i).toString();
        }
        return res + ")";
    }

    public String toTokenString() {
        String res = "";
        for (int i = 0; i < constituents.size(); ++i) {
            res +=  constituents.get(i).toString() + "\n";
        }
        return res;
    }
}