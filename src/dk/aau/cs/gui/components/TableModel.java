package dk.aau.cs.gui.components;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import dk.aau.cs.verification.batchProcessing.BatchProcessingVerificationResult;

public class TableModel extends AbstractTableModel {
	private static final long serialVersionUID = 1L;
	private final String[] HEADINGS = new String[]{ "Model", "Query", "Result", "Verification Time" }; // TODO: add expected result and maybe error columns
	private List<BatchProcessingVerificationResult> results;
	
	public TableModel(){
		results = new ArrayList<BatchProcessingVerificationResult>();
	}
	
	public void AddResult(BatchProcessingVerificationResult result){
		int lastRow = results.size();
		results.add(result);
		fireTableRowsInserted(lastRow, lastRow);
	}
		
	@Override
	public String getColumnName(int column) {
		return HEADINGS[column];
	}
	
	@Override
	public int getColumnCount() {
		return HEADINGS.length;
	}

	@Override
	public int getRowCount() {
		return results.size();
	}

	@Override
	public Object getValueAt(int row, int col) {
		BatchProcessingVerificationResult result = results.get(row);
		
		switch(col){
		case 0: return result.modelFile();
		case 1: return result.queryName();
		case 2: return result.isQuerySatisfied() ? "Satisfied" : "Not Satisfied";		
		case 3: return (result.verificationTime() / 1000.0) + " s";
		//case 4: return result.error();
		default:
			return null;
		}
	}

	public void clear() {
		results.clear();
		fireTableDataChanged();
	}
	
}
