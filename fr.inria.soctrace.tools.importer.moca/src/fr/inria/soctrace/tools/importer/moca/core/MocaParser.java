/*******************************************************************************
 * Copyright (c) 2012-2015 Generoso Pagano.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Generoso Pagano - initial API and implementation
 *     David Beniamine - Adaptation from PjDump to HeapInfo
 *     Youenn Corre - Adaptation from HeapInfo to Moca
 ******************************************************************************/
package fr.inria.soctrace.tools.importer.moca.core;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.soctrace.lib.model.Event;
import fr.inria.soctrace.lib.model.EventParam;
import fr.inria.soctrace.lib.model.EventParamType;
import fr.inria.soctrace.lib.model.EventProducer;
import fr.inria.soctrace.lib.model.EventType;
import fr.inria.soctrace.lib.model.Variable;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.storage.SystemDBObject;
import fr.inria.soctrace.lib.storage.TraceDBObject;
import fr.inria.soctrace.lib.utils.IdManager;
import fr.inria.soctrace.tools.importer.moca.core.MocaConstants;
import fr.inria.soctrace.tools.importer.moca.core.MocaConstants.MocaTraceType;

/**
 * Moca Parser core class.
 * 
 * Warning: the current implementation of this parser works under the hypothesis
 * that a producer may be in a single state at a given time.
 * 
 * @author "David Beniamine <David.Beniamine@imag.fr>"
 */
public class MocaParser {

	private static final Logger logger = LoggerFactory
			.getLogger(MocaParser.class);

	private SystemDBObject sysDB;
	private Map<MocaTraceType, TraceDBObject> traceDB =  new HashMap<MocaTraceType, TraceDBObject>();
	private List<String> traceFiles;

	private Map<MocaTraceType, Map<String, EventProducer>> producersMap = new HashMap<MocaTraceType, Map<String, EventProducer>>();
	private Map<String, EventType> types = new HashMap<String, EventType>();
	private Map<String, MocaLineParser> parserMap = new HashMap<String, MocaLineParser>();
	private Map<MocaTraceType, List<EventProducer>> currentProducers = new HashMap<MocaTraceType, List<EventProducer>>();
	private Map<MocaTraceType, List<EventProducer>> allProducers = new HashMap<MocaTraceType, List<EventProducer>>();
	private ArrayList<MocaTraceType> activeTypes = new ArrayList<MocaTraceType>();
	
	private int numberOfEvents = 0;
	private int page = 0;
	private IdManager eIdManager = new IdManager();
	private IdManager etIdManager = new IdManager();
	private IdManager epIdManager = new IdManager();
	private IdManager eptIdManager = new IdManager();
	private void saveTraceMetadata(MocaTraceType aTraceType) throws SoCTraceException {
		String alias = FilenameUtils.getBaseName(traceDB.get(aTraceType).getDBName());
		MocaTraceMetadata metadata = new MocaTraceMetadata(sysDB,
				traceDB.get(aTraceType).getDBName(), alias, numberOfEvents);
		metadata.createMetadata();
		metadata.saveMetadata();
		sysDB.commit();
	}

	private Long currStart = -1l, currEnd = -1l;
	private Map<MocaTraceType, EventProducer> root = new HashMap<MocaTraceType, EventProducer>();
	private Map<MocaTraceType, EventProducer> currentEP = new HashMap<MocaTraceType, EventProducer>();
	private Map<MocaTraceType, List<Long>> ignoredEventProd = new HashMap<MocaTraceType, List<Long>>();

	private Map<MocaTraceType, List<Event>> eventList = new HashMap<MocaTraceType, List<Event>>();
	
	private int memoryPageSize = 4096;
	private int maxLevelOfMerging = 4;
	private boolean trimLoneEventProducers;
	
	public MocaParser(SystemDBObject sysDB, HashMap<MocaTraceType, TraceDBObject> tracesDB,
			List<String> traceFileName, boolean trimLoneEP, int maxLevelOfmerging) {

		this.traceFiles = traceFileName;
		this.sysDB = sysDB;
		this.traceDB = tracesDB;
		this.maxLevelOfMerging = maxLevelOfmerging;
		this.trimLoneEventProducers = trimLoneEP;
		activeTypes.addAll(traceDB.keySet());
		initCollections();
		
		// Add the different parser
		parserMap.put(MocaConstants.TASK, new TaskParser());
		parserMap.put(MocaConstants.ACCESS, new AccessParser());
		parserMap.put(MocaConstants.CHUNK, new ChunkParser());

		// Create New Structure events 
		EventType et = new EventType(etIdManager.getNextId(),
				EventCategory.PUNCTUAL_EVENT);
		et.setName(MocaConstants.NEW_CHUNK_EVENT);

		// and all the associated param types 
		EventParamType ept;
		for (int i = 0; i < MocaConstants.NEW_CHUNK_PARAM.length; i++) {
			ept = new EventParamType(eptIdManager.getNextId());
			ept.setEventType(et);
			ept.setName(MocaConstants.NEW_CHUNK_PARAM[i]);
			ept.setType(MocaConstants.NEW_CHUNK_PARAM_TYPES[i]);
		}
		types.put(et.getName(), et);

		// Create Access events 
		for (int j = 0; j < MocaConstants.ACCESS_EVENTS.length; j++) {
			et = new EventType(etIdManager.getNextId(), EventCategory.VARIABLE);
			et.setName(MocaConstants.ACCESS_EVENTS[j]);

			// and all the associated param types 
			for (int i = 0; i < MocaConstants.ACCESS_PARAM.length; i++) {
				ept = new EventParamType(eptIdManager.getNextId());
				ept.setEventType(et);
				ept.setName(MocaConstants.ACCESS_PARAM[i]);
				ept.setType(MocaConstants.ACCESS_PARAM_TYPES[i]);
			}
			types.put(et.getName(), et);
		}
		
		for (MocaTraceType aTraceType : activeTypes) {
			EventProducer rootEp = new EventProducer(epIdManager.getNextId());
			String rootName = "MemoryRoot";
			
			if (aTraceType == MocaTraceType.TASK_PHYSICAL_ADDRESSING
					|| aTraceType == MocaTraceType.TASK_VIRTUAL_ADDRESSING)
				rootName = "Moca Launcher";

			rootEp.setName(rootName);
			rootEp.setParentId(EventProducer.NO_PARENT_ID);
			rootEp.setLocalId(String.valueOf(rootEp.getId()));

			root.put(aTraceType, rootEp);
			producersMap.get(aTraceType).put(rootName, rootEp);
			allProducers.get(aTraceType).add(rootEp);
		}
		
		numberOfEvents = 0;
		page = 0;
	}

	/**
	 * 
	 * @param monitor
	 *            progress monitor
	 * @throws SoCTraceException
	 */
	public void parseTrace(IProgressMonitor monitor) throws SoCTraceException {
		monitor.beginTask("Parsing Trace", traceFiles.size());

		// Files containing event producers info
		List<String> producerFiles = new LinkedList<String>();
		// Files containing trace info
		List<String> eventFiles = new LinkedList<String>();
		
		for (String aTraceFile : traceFiles) {
			// If trace info file type
			if(aTraceFile.contains(MocaConstants.TRACE_FILE_TYPE))
				eventFiles.add(aTraceFile);
			else
				producerFiles.add(aTraceFile);
		}

		for (String aTraceFile : producerFiles) {
			monitor.subTask("Building Event Producer...");
			parseEventProd(monitor, aTraceFile);
		}

		if (monitor.isCanceled()) {
			for (TraceDBObject aTraceDB : traceDB.values())
				aTraceDB.dropDatabase();

			sysDB.rollback();
			return;
		}
		
		for (String aTraceFile : eventFiles) {
			monitor.subTask(aTraceFile);
			logger.debug("Trace file: {}", aTraceFile);

			// Trace Events, EventTypes and Producers
			parseRawTrace(monitor, aTraceFile);
			monitor.worked(1);
			
			if (monitor.isCanceled()) {
				for(TraceDBObject aTraceDB: traceDB.values())
					aTraceDB.dropDatabase();
				
				sysDB.rollback();
				return;
			}
		}
		
		//createIntermediateEP();
		
		for (MocaTraceType currentTraceType : activeTypes) {
			saveProducers(currentTraceType);
			saveTypes(currentTraceType);
			saveTraceMetadata(currentTraceType);
		}
	}

	private void parseRawTrace(IProgressMonitor monitor, String aTraceFile) throws SoCTraceException {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					new DataInputStream(new FileInputStream(aTraceFile))));
			String[] line;
			MocaLineParser parser;
			//for (MocaTraceType aTraceType : activeTypes)
				//if (aTraceType != MocaTraceType.PHYSICAL_ADDRESSING
				//		&& aTraceType != MocaTraceType.VIRTUAL_ADDRESSING)
					//currentProducers.put(aTraceType,
					//		new LinkedList<EventProducer>());

			while ((line = getLine(br)) != null) {

				logger.debug(Arrays.toString(line));
				// if the line isn't recognized, assume it is a debug line or a
				// valgrind stuff
				if ((parser = parserMap.get(line[MocaConstants.ENTITY])) != null)
					parser.parseLine(line);

				for (MocaTraceType currentTraceType : activeTypes) {
					if (eventList.get(currentTraceType).size() == MocaConstants.DB_PAGE_SIZE)
						page++;

					// XXX
					if (eventList.get(currentTraceType).size() >= MocaConstants.DB_PAGE_SIZE) {
						saveEvents(eventList.get(currentTraceType), currentTraceType);
						numberOfEvents += eventList.get(currentTraceType).size();
						eventList.get(currentTraceType).clear();
					}
					
					if (monitor.isCanceled()) {
						return;
					}
				}
			}
			
			for (MocaTraceType currentTraceType : activeTypes) {
				if (eventList.get(currentTraceType).size() > 0) {
					saveEvents(eventList.get(currentTraceType), currentTraceType);
					numberOfEvents += eventList.get(currentTraceType).size();
					eventList.get(currentTraceType).clear();

					if (monitor.isCanceled()) {
						return;
					}
				}
			}

			logger.debug("Saved {} events on {} pages", numberOfEvents,
					(page + 1));

		} catch (Exception e) {
			throw new SoCTraceException(e);
		}

	}

	/**
	 * Save the events of a page in the trace DB.
	 * 
	 * @param events
	 *            events list
	 * @throws SoCTraceException
	 */
	private void saveEvents(List<Event> events, MocaTraceType aTraceType)
			throws SoCTraceException {
		for (Event e : events) {
			try {
				e.check();
			} catch (SoCTraceException ex) {
				logger.debug(ex.getMessage());
				throw new SoCTraceException(ex);
			}

			traceDB.get(aTraceType).save(e);
			for (EventParam ep : e.getEventParams()) {
				traceDB.get(aTraceType).save(ep);
			}
		}
		traceDB.get(aTraceType).commit(); // committing each page is faster
	}

	/**
	 * Get an event record from the given reader.
	 * 
	 * @param br
	 *            reader
	 * @return the record or null if the file is finished
	 * @throws IOException
	 */
	private String[] getLine(BufferedReader br) throws IOException {
		String strLine = null;
		String[] args = null;
		while (args == null) {
			if ((strLine = br.readLine()) == null)
				return null;

			strLine = strLine.trim();
			if (strLine.equals(""))
				continue;
			if (strLine.startsWith("#"))
				continue;

			args = strLine.split(MocaConstants.SEPARATOR);
		}
		return args;
	}

	private void saveProducers(MocaTraceType aTraceType) throws SoCTraceException {
		Collection<EventProducer> eps = allProducers.get(aTraceType);
		for (EventProducer ep : eps) {
			traceDB.get(aTraceType).save(ep);
		}

		logger.debug("For trace type: " + aTraceType + ", " + eps.size()
				+ " event producers were saved");
		
		traceDB.get(aTraceType).commit();
	}

	private void saveTypes(MocaTraceType aTraceType) throws SoCTraceException {
		for (EventType et : types.values()) {
			traceDB.get(aTraceType).save(et);
			for (EventParamType ept : et.getEventParamTypes()) {
				traceDB.get(aTraceType).save(ept);
			}
		}
	}

	private EventProducer findProducer(long addr, MocaTraceType currentTraceType) {

		for (EventProducer ep : allProducers.get(currentTraceType)) {
			try {
				long newEpAddr = Long.parseLong(ep.getName());
				if (addr == newEpAddr) {
					return ep;
				}
			} catch (NumberFormatException e) {
				continue;
			}
		}
		
		// If we did not find the producer, create it
		return null;// createProducer(addr, currentTraceType);
	}

	// Return 1 if access is shared, 0 else
	private int isShared(int cpuMask) {
		int m = 1, nth = 0;
		while (m <= cpuMask) {
			if ((m & cpuMask) == m) {
				nth++;
				if (nth > 1)
					return MocaConstants.A_Type_Shared;
			}
			m = m << 1;
		}
		
		return MocaConstants.A_Type_Private;
	}
	
	/**
	 * Create a new event producer
	 * 
	 * @param anAddress
	 *            producer name
	 * @param ppid
	 *            producer parent id
	 * @return the newly created EP
	 */
	private EventProducer createProducer(long anAddress, int ppid) {
		EventProducer newEP = new EventProducer(epIdManager.getNextId());

		newEP.setName(Long.toString(anAddress));
		newEP.setParentId(ppid);
		newEP.setType("Memory Zone");
		newEP.setLocalId(String.valueOf(newEP.getId()));

		return newEP;
	}

	/**
	 * Create an event prpducer for a specific trace type
	 * 
	 * @param anAddress
	 *            producer name
	 * @param currentTraceType
	 * @return the newly created EP
	 */
	private EventProducer createProducer(long anAddress,
			MocaTraceType currentTraceType) {
		EventProducer newEP = createProducer(anAddress,
				currentEP.get(currentTraceType).getId());
		producersMap.get(currentTraceType).put(Long.toHexString(anAddress),
				newEP);
		currentProducers.get(currentTraceType).add(newEP);
		allProducers.get(currentTraceType).add(newEP);

		return newEP;
	}

	private class AccessParser implements MocaLineParser {
		public void parseLine(String[] fields) throws SoCTraceException {
			// 1 if the access is shared among threads, 0 else
			int shared_type = isShared(Integer.parseInt(
					fields[MocaConstants.A_CPUMask], 2));
			
			// Number of read, write	
			double access_nbr[] = {
					new Double(fields[MocaConstants.A_NReads]),
					new Double(fields[MocaConstants.A_NWrites]) };
			
			for (MocaTraceType currentTraceType : activeTypes) {
				// Convert from base 16
				EventProducer prod;
				long address;

				if (currentTraceType == MocaTraceType.TASK_VIRTUAL_ADDRESSING) {
					address = Long.parseLong(fields[MocaConstants.A_VirtAddr],
							16);
				} else {
					address = Long.parseLong(fields[MocaConstants.A_PhysAddr],
							16);
				}

				// If the event producer was removed
				if(ignoredEventProd.get(currentTraceType).contains(address))
					// Ignore the event
					continue;
				
				prod = findProducer(address, currentTraceType);

				// Create one event for reads, one for writes
				for (int type = MocaConstants.A_Type_Read; type <= MocaConstants.A_Type_Write; type++) {
					if (access_nbr[type] > 0) {
						Variable v = new Variable(eIdManager.getNextId());
						EventType et = types
								.get(MocaConstants.ACCESS_EVENTS[shared_type
										+ type]);
						v.setType(et);
						v.setEventProducer(prod);
						v.setPage(page);
						v.setTimestamp(currStart);
						v.setEndTimestamp(currEnd);
						v.setValue(access_nbr[type]);

						// Add params
						for (EventParamType ept : et.getEventParamTypes()) {
							EventParam ep = new EventParam(
									epIdManager.getNextId());
							ep.setEvent(v);
							ep.setEventParamType(ept);
							// Set the value to the right field according to
							// ept.name
							for (int i = 0; i < MocaConstants.ACCESS_PARAM.length; i++) {
								if (ept.getName().equals(
										MocaConstants.ACCESS_PARAM[i])) {
									// the field of the ith param is i+1
									ep.setValue(fields[i
											+ MocaConstants.A_Param_offset]);
									break;
								}
							}
						}
						eventList.get(currentTraceType).add(v);
					}
				}
			}
		}
	}

	private class TaskParser implements MocaLineParser {
		public void parseLine(String[] fields) throws SoCTraceException {
			if (producersMap.containsKey(fields[MocaConstants.T_PID]))
				return;

			for (MocaTraceType currentTraceType : activeTypes) {
				if (currentTraceType == MocaTraceType.VIRTUAL_ADDRESSING
						|| currentTraceType == MocaTraceType.PHYSICAL_ADDRESSING) {
					currentEP.put(currentTraceType, root.get(currentTraceType));
					continue;
				}
				
				// Create the event producer
				EventProducer ep = new EventProducer(epIdManager.getNextId());
				ep.setName(fields[MocaConstants.T_PID]);
				// No parents (root)
				ep.setParentId(root.get(currentTraceType).getId());
				ep.setType(fields[MocaConstants.ENTITY]);
				ep.setLocalId(String.valueOf(ep.getId()));

				producersMap.get(currentTraceType).put(
						fields[MocaConstants.T_PID], ep);
				allProducers.get(currentTraceType).add(ep);
				currentEP.put(currentTraceType, ep);
				
				if(fields[MocaConstants.T_ID].equals(MocaConstants.TASK_ZERO))
					memoryPageSize = Integer.valueOf(fields[MocaConstants.MEM_PAGE_SIZE]);

				if (memoryPageSize == -1) {
					logger.error("Error getting memory page size.");
				}
			}
		}
	}
	
	private class ChunkParser implements MocaLineParser {

		public void parseLine(String[] fields) {
			// Set the start and end dates for the current time
			currStart = Long.parseLong(fields[MocaConstants.C_Start]);
			currEnd = Long.parseLong(fields[MocaConstants.C_End]);
		}
	}

	/**
	 * Initialize collections for all active trace types
	 */
	private void initCollections() {
		for (MocaTraceType aTraceType : activeTypes) {
			producersMap.put(aTraceType, new HashMap<String, EventProducer>());
			currentProducers.put(aTraceType, new LinkedList<EventProducer>());
			allProducers.put(aTraceType, new LinkedList<EventProducer>());
			eventList.put(aTraceType, new LinkedList<Event>());
			ignoredEventProd.put(aTraceType, new LinkedList<Long>());
		}
	}

	private void createIntermediateEP(MocaTraceType aTraceType) {
			logger.debug(aTraceType.toString());
			allProducers.get(aTraceType).addAll(
					mergeProducer(allProducers.get(aTraceType), 0.0));
			
			// Do not trim if there was no merging
			if(trimLoneEventProducers && maxLevelOfMerging > 0)
				removeSingleNode(aTraceType);
	}
	
	List<EventProducer> mergeProducer(List<EventProducer> eventProdToMerge,
			double levelOfmerging) {
		LinkedList<EventProducer> newEventProd = new LinkedList<EventProducer>();

		// Sort by parent id, and then by ascending address
		Collections.sort(eventProdToMerge, new Comparator<EventProducer>() {
			@Override
			public int compare(final EventProducer arg0,
					final EventProducer arg1) {
				if (arg0.getParentId() == arg1.getParentId())
					return arg0.getName().compareTo(arg1.getName());
				else
					return arg0.getParentId() - arg1.getParentId();
			}
		});

		int newMemPageSize = (int) (memoryPageSize * (Math.pow(2.0, levelOfmerging)));
		
		for (int i = 0; i < eventProdToMerge.size() - 1; i += 2) {
			try {
				if (eventProdToMerge.get(i).getParentId() != eventProdToMerge
						.get(i + 1).getParentId()) {
					i--;
					continue;
				}

				long addrEp1 = Long.valueOf(eventProdToMerge.get(i).getName());
				long addrEp2 = Long.valueOf(eventProdToMerge.get(i + 1)
						.getName());

				// If we can merge them
				if (addrEp2 - addrEp1 == newMemPageSize) {
					// Create a new EP with lowest address as name
					EventProducer newEP = createProducer(addrEp1,
							eventProdToMerge.get(i).getParentId());
					newEventProd.add(newEP);

					// Update parent ID of previous producers
					eventProdToMerge.get(i).setParentId(newEP.getId());
					eventProdToMerge.get(i + 1).setParentId(newEP.getId());
				} else {
					// If the current pair was not a match, advance only of
					// one
					i--;
				}
			} catch (NumberFormatException e) {
				continue;
			}
		}

		logger.debug(levelOfmerging + ", " + newMemPageSize + ", " + newEventProd.size());
		
		levelOfmerging++;
		if (levelOfmerging < maxLevelOfMerging)
			newEventProd.addAll(mergeProducer(newEventProd, levelOfmerging));

		return newEventProd;
	}
	
	/**
	 * Find and remove the event producers that have not been merged
	 * 
	 * @param aTraceType
	 */
	private void removeSingleNode(MocaTraceType aTraceType) {
		int removedEp = 0;
		// Event producers that are not a direct child of the root EP (or the
		// root itself)
		ArrayList<EventProducer> notRootChildren = new ArrayList<EventProducer>();
		// Event producers that are direct children of the root EP
		ArrayList<EventProducer> rootChildren = new ArrayList<EventProducer>();

		for (EventProducer aProd : allProducers.get(aTraceType)) {
			if (aProd.getParentId() == root.get(aTraceType).getId())
				rootChildren.add(aProd);
			else if (aProd.getId() != root.get(aTraceType).getId())
				notRootChildren.add(aProd);
		}

		for (EventProducer aRootChild : rootChildren) {
			boolean hasChild = false;
			for (EventProducer aProd : notRootChildren) {
				if (aProd.getParentId() == aRootChild.getId()) {
					hasChild = true;
					break;
				}
			}

			if (!hasChild) {
				allProducers.get(aTraceType).remove(aRootChild);
				ignoredEventProd.get(aTraceType).add(
						Long.valueOf(aRootChild.getName()));
				removedEp++;
			}
		}
		logger.debug("Removed EPs: " + removedEp);
	}
	
	private void parseEventProd(IProgressMonitor monitor, String aTraceFile) {
		MocaTraceType currentTraceType;
		boolean virtual;
		
		if (aTraceFile.contains("Physical")) {
			currentTraceType = MocaTraceType.PHYSICAL_ADDRESSING;
			virtual = false;
		} else {
			currentTraceType = MocaTraceType.VIRTUAL_ADDRESSING;
			virtual = true;
		}

		LinkedList<EventProducer> newEventProd = new LinkedList<EventProducer>();
		HashMap<Long, List<String>> producerTaskIndex = new HashMap<Long, List<String>>();
		BufferedReader br;
		try {
			br = new BufferedReader(new InputStreamReader(new DataInputStream(
					new FileInputStream(aTraceFile))));

			String[] line;

			while ((line = getLine(br)) != null) {
				if (line.length <= 1)
					continue;

				long addr = Long.valueOf(line[0], 16);
				producerTaskIndex.put(addr, new LinkedList<String>());
				// Create a producer with root pid
				EventProducer anEP = createProducer(addr,
						root.get(currentTraceType).getId());
				if (currentTraceType == MocaTraceType.VIRTUAL_ADDRESSING
						|| currentTraceType == MocaTraceType.PHYSICAL_ADDRESSING) {
					newEventProd.add(anEP);
				}

				for (int i = 1; i < line.length; i++) {
					producerTaskIndex.get(addr).add(line[i]);
				}
			}

			allProducers.get(currentTraceType).addAll(newEventProd);
				//	mergeProducer(newEventProd, 0, currentTraceType,
				//			new HashMap<EventProducer, LinkedList<Long>>()));
			
			createIntermediateEP(currentTraceType);
			
			//createTaskEventProd(producerTaskIndex, virtual);
			

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}
