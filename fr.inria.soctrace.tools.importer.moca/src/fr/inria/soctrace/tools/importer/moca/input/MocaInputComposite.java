package fr.inria.soctrace.tools.importer.moca.input;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;

import fr.inria.soctrace.framesoc.core.tools.model.IFramesocToolInput;
import fr.inria.soctrace.framesoc.ui.input.DefaultImporterInputComposite;
import fr.inria.soctrace.framesoc.ui.listeners.LaunchTextListener;

public class MocaInputComposite extends DefaultImporterInputComposite {
	
	private boolean trimLonelyProducer = false;
	private int maxHierarchyDepth = 16;
	
	public MocaInputComposite(Composite parent, int style) {
		super(parent, style);
		
		setLayout(new GridLayout(2, false));

		final Button btnTrimLoneEPButton = new Button(this, SWT.CHECK);
		btnTrimLoneEPButton.setText("Remove Isolated Producers");
		btnTrimLoneEPButton.setSelection(trimLonelyProducer);
		btnTrimLoneEPButton.setToolTipText("Remove event producers that has not been merged.");
		btnTrimLoneEPButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				trimLonelyProducer = btnTrimLoneEPButton.getSelection();
			}
		});
		new Label(this, SWT.NONE);
		
		final Label hierarchyDepth = new Label(this, SWT.NONE);
		hierarchyDepth.setText("Max. Hierarchy Depth");
		
		final Spinner maxHierarDepthSpinner = new Spinner(this, SWT.BORDER	);
		maxHierarDepthSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		maxHierarDepthSpinner.setToolTipText("");
		maxHierarDepthSpinner.setMinimum(0);
		maxHierarDepthSpinner.setMaximum(10000);
		maxHierarDepthSpinner.setSelection(maxHierarchyDepth);
		maxHierarDepthSpinner.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				maxHierarchyDepth = maxHierarDepthSpinner.getSelection();
			}
		});
	}

	
	@Override
	public IFramesocToolInput getToolInput() {
		MocaInput input = new MocaInput();
		input.setFiles(Arrays.asList(LaunchTextListener.getTokens(traceFileListener.getText())));
		input.setMaxHierarchyDepth(maxHierarchyDepth);
		input.setTrimLonelyProducer(trimLonelyProducer);
		return input;
	}
}
