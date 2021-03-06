/*******************************************************************************
 * Copyright (c) 2012-2014 Generoso Pagano, David Beniamine.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Generoso Pagano - initial API and implementation
 *     David Beniamine - Adaptation from PjDump to HeapInfo
 ******************************************************************************/
package fr.inria.soctrace.tools.importer.moca;

import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import fr.inria.soctrace.framesoc.core.FramesocManager;
import fr.inria.soctrace.framesoc.core.tools.management.PluginImporterJob;
import fr.inria.soctrace.framesoc.core.tools.model.FramesocTool;
import fr.inria.soctrace.framesoc.core.tools.model.IFramesocToolInput;
import fr.inria.soctrace.framesoc.core.tools.model.IPluginToolJobBody;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.storage.DBObject.DBMode;
import fr.inria.soctrace.lib.storage.SystemDBObject;
import fr.inria.soctrace.lib.storage.TraceDBObject;
import fr.inria.soctrace.lib.utils.Configuration;
import fr.inria.soctrace.lib.utils.Configuration.SoCTraceProperty;
import fr.inria.soctrace.lib.utils.DeltaManager;
import fr.inria.soctrace.tools.importer.moca.core.MocaConstants;
import fr.inria.soctrace.tools.importer.moca.core.MocaConstants.MocaTraceType;
import fr.inria.soctrace.tools.importer.moca.core.MocaParser;
import fr.inria.soctrace.tools.importer.moca.input.MocaInput;

/**
 * Moca importer tool.
 *
 * @author "David Beniamine <David.Beniamine@imag.fr>"
 * @author "Generoso Pagano <Generoso.Pagano@inria.fr>"
 */
public class MocaImporter extends FramesocTool {

	private final static Logger logger = LoggerFactory
			.getLogger(MocaImporter.class);

	/**
	 * Plugin Tool Job body: we use a Job since we have to perform a long
	 * operation and we don't want to freeze the UI.
	 */
	public class MocaImporterPluginJobBody implements IPluginToolJobBody {

		private MocaInput input;

		public MocaImporterPluginJobBody(IFramesocToolInput input) {
			this.input = (MocaInput) input;
		}

		@Override
		public void run(IProgressMonitor monitor) {
			DeltaManager delta = new DeltaManager();

			logger.debug("Args: ");
			List<String> files = input.getFiles();

			for (String s : files) {
				logger.debug(s);
			}

			boolean trimLoneEP = input.isTrimLonelyProducer();
			boolean trimOutOfStructs = input.isStructsOnly();
			int maxHierarchyDepth = input.getMaxHierarchyDepth();

			String pattern = Pattern
					.quote(System.getProperty("file.separator"));

			delta.start();

			String sysDbName = Configuration.getInstance().get(
					SoCTraceProperty.soctrace_db_name);

			String t[] = files.get(0).split(pattern);
			String t2 = t[t.length - 1];
			if (t2.endsWith(MocaConstants.TRACE_EXT))
				t2 = t2.replace(MocaConstants.TRACE_EXT, "");
			String traceName;

			// Set trace name as the directory name
			if (t.length - 2 >= 0)
				traceName = t[t.length - 2];
			else
				traceName = "Moca";

			String traceDbName = FramesocManager.getInstance().getTraceDBName(
					traceName);

			SystemDBObject sysDB = null;
			HashMap<MocaTraceType, TraceDBObject> tracesDB = new HashMap<MocaTraceType, TraceDBObject>();
			TraceDBObject traceDBVirt = null, traceDBPhys = null, traceDBTaskPhys = null, traceDBTaskVirt = null;

			try {
				// open system DB
				sysDB = new SystemDBObject(sysDbName, DBMode.DB_OPEN);
				// create new trace DB
				traceDBTaskVirt = new TraceDBObject(traceDbName + "_Task_Virtual",
						DBMode.DB_CREATE);
				traceDBTaskPhys = new TraceDBObject(traceDbName + "_Task_Physical",
						DBMode.DB_CREATE);
				traceDBVirt = new TraceDBObject(traceDbName + "_Virtual",
						DBMode.DB_CREATE);
				traceDBPhys = new TraceDBObject(traceDbName + "_Physical",
						DBMode.DB_CREATE);
				
				tracesDB.put(MocaTraceType.TASK_VIRTUAL_ADDRESSING, traceDBTaskVirt);
				tracesDB.put(MocaTraceType.TASK_PHYSICAL_ADDRESSING, traceDBTaskPhys);
				tracesDB.put(MocaTraceType.VIRTUAL_ADDRESSING, traceDBVirt);
				tracesDB.put(MocaTraceType.PHYSICAL_ADDRESSING, traceDBPhys);
				// parsing
				MocaParser parser = new MocaParser(sysDB, tracesDB, files, trimLoneEP, trimOutOfStructs, maxHierarchyDepth);
				parser.parseTrace(monitor);

				// close the traces DB and the system DB (commit)
				for (TraceDBObject aTraceDB : tracesDB.values()) {
					aTraceDB.close();
				}

				sysDB.close();

			} catch (SoCTraceException ex) {
				logger.error(ex.getMessage());
				ex.printStackTrace();
				logger.error("Import failure. Trying to rollback modifications in DB.");
				if (sysDB != null)
					try {
						sysDB.rollback();
					} catch (SoCTraceException e) {
						e.printStackTrace();
					}

				for (TraceDBObject aTraceDB : tracesDB.values()) {
					if (aTraceDB != null)
						try {
							aTraceDB.dropDatabase();
						} catch (SoCTraceException e) {
							e.printStackTrace();
						}
				}
			} finally {
				delta.end("Import trace");
			}
		}
	}

	@Override
	public void launch(IFramesocToolInput input) {
		PluginImporterJob job = new PluginImporterJob("Moca Importer",
				new MocaImporterPluginJobBody(input));
		job.setUser(true);
		job.schedule();
	}

	@Override
	public ParameterCheckStatus canLaunch(IFramesocToolInput input) {
		MocaInput args = (MocaInput) input;

		if (args.getFiles().size() < 1)
			return new ParameterCheckStatus(false, "Not enough arguments.");

		for (String file : args.getFiles()) {
			File f = new File(file);
			if (!f.isFile())
				return new ParameterCheckStatus(false, f.getName()
						+ " does not exist.");
		}

		return new ParameterCheckStatus(true, "");
	}

}
