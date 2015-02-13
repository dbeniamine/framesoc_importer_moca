package fr.inria.soctrace.tools.importer.moca.input;

import java.util.List;

import fr.inria.soctrace.framesoc.core.tools.model.IFramesocToolInput;

public class MocaInput implements IFramesocToolInput {
	
	protected List<String> files;
	private boolean trimLonelyProducer = false;
	private int maxHierarchyDepth = 4;

	@Override
	public String getCommand() {
		return "";
	}

	public List<String> getFiles() {
		return files;
	}

	public void setFiles(List<String> files) {
		this.files = files;
	}

	public boolean isTrimLonelyProducer() {
		return trimLonelyProducer;
	}

	public void setTrimLonelyProducer(boolean trimLonelyProducer) {
		this.trimLonelyProducer = trimLonelyProducer;
	}

	public int getMaxHierarchyDepth() {
		return maxHierarchyDepth;
	}

	public void setMaxHierarchyDepth(int maxHierarchyDepth) {
		this.maxHierarchyDepth = maxHierarchyDepth;
	}

}
