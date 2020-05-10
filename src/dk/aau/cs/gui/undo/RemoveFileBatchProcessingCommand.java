package dk.aau.cs.gui.undo;

import dk.aau.cs.gui.BatchProcessingDialog;
import dk.aau.cs.gui.SharedPlacesAndTransitionsPanel;
import dk.aau.cs.model.tapn.SharedPlace;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class RemoveFileBatchProcessingCommand extends Command{
    private DefaultListModel<File> listModel;
    List<File> files;
    File file;
    private BatchProcessingDialog dialog;
    public RemoveFileBatchProcessingCommand(DefaultListModel<File> listModel, File file, List<File> files, BatchProcessingDialog dialog){
        this.listModel = listModel;
        this.file = file;
        this.files = files;
        this.dialog = dialog;
    }

    @Override
    public void redo() {
        files.remove(file);
        listModel.removeElement(file);
        dialog.enableButtons();
    }

    @Override
    public void undo() {
        files.add(file);
        listModel.addElement(file);
        dialog.enableButtons();
    }
}
