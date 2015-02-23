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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	private Map<String, EventType> types = new HashMap<String, EventType>();
	private Set<Long> sharedAddress= new HashSet<Long>();
	private Map<String, MocaLineParser> parserMap = new HashMap<String, MocaLineParser>();
	private Map<MocaTraceType, List<EventProducer>> allProducers = new HashMap<MocaTraceType, List<EventProducer>>();
	private List<MocaTraceType> activeTypes = new ArrayList<MocaTraceType>();
	private Map<MocaTraceType, List<List<EventProducer>>> consecutiveProducers = new HashMap<MocaTraceType, List<List<EventProducer>>>();
	
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
	// Removed event producers during the trimming steps
	private Map<MocaTraceType, List<Long>> ignoredEventProd = new HashMap<MocaTraceType, List<Long>>();
	private Map<MocaTraceType, List<Event>> eventList = new HashMap<MocaTraceType, List<Event>>();
	
	private Map<MocaTraceType, Map<String, EventProducer>> producersIndex = new HashMap<MocaTraceType, Map<String, EventProducer>>();
	private Map<MocaTraceType, Map<String, Map<String, EventProducer>>> taskProducersIndex = new HashMap<MocaTraceType, Map<String, Map<String, EventProducer>>>();
	
	private String currentTask;
	private int memoryPageSize = -1;
	private int maxHierarchyDepth = 4;
	private boolean trimLoneEventProducers;
	
	public MocaParser(SystemDBObject sysDB, HashMap<MocaTraceType, TraceDBObject> tracesDB,
			List<String> traceFileName, boolean trimLoneEP, int maxLevelOfmerging) {

		this.traceFiles = traceFileName;
		this.sysDB = sysDB;
		this.traceDB = tracesDB;
		this.maxHierarchyDepth = maxLevelOfmerging;
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
			String rootName = MocaConstants.MEMORY_ROOT_NAME;
			
			if (aTraceType == MocaTraceType.TASK_PHYSICAL_ADDRESSING
					|| aTraceType == MocaTraceType.TASK_VIRTUAL_ADDRESSING)
				rootName = MocaConstants.TASK_ROOT_NAME;

			rootEp.setName(rootName);
			rootEp.setParentId(EventProducer.NO_PARENT_ID);
			rootEp.setLocalId(String.valueOf(rootEp.getId()));

			root.put(aTraceType, rootEp);
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
			monitor.subTask(aTraceFile + ": Building Event Producers...");
			parseEventProd(monitor, aTraceFile);
			monitor.worked(1);
		}

		if (monitor.isCanceled()) {
			for (TraceDBObject aTraceDB : traceDB.values())
				aTraceDB.dropDatabase();

			sysDB.rollback();
			return;
		}
		
		for (String aTraceFile : eventFiles) {
			monitor.subTask(aTraceFile + ": Building Events...");
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
		
		monitor.subTask("Finalizing...");
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

		System.err.println("For trace type: " + aTraceType + ", " + eps.size()
				+ " event producers were saved");
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

		if (currentTraceType == MocaTraceType.VIRTUAL_ADDRESSING
				|| currentTraceType == MocaTraceType.PHYSICAL_ADDRESSING) {
			try {
				return producersIndex.get(currentTraceType).get(
						String.valueOf(addr));
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			try {
				return taskProducersIndex.get(currentTraceType)
						.get(currentTask).get(String.valueOf(addr));
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}

	// Return 1 if access is shared, 0 else
	private int isShared(long address) {
		if (sharedAddress.contains(address))
			return MocaConstants.A_Type_Shared;
		
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
		return createProducer(String.valueOf(anAddress), ppid);
	}

	private EventProducer createProducer(String aName, int ppid) {
		EventProducer newEP = new EventProducer(epIdManager.getNextId());

		newEP.setName(aName);
		newEP.setParentId(ppid);
		newEP.setType("Memory Zone");
		newEP.setLocalId(String.valueOf(newEP.getId()));

		return newEP;
	}

	private class AccessParser implements MocaLineParser {
		public void parseLine(String[] fields) throws SoCTraceException {

			
			// 1 if the access is shared among threads, 0 else
			int shared_type = 0;
			
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
				shared_type = isShared(address);
				
				// If the event producer was removed
				if(ignoredEventProd.get(currentTraceType).contains(address))
					// Ignore the event
					continue;
				
				prod = findProducer(address, currentTraceType);
				if (prod == null) {
					if (!trimLoneEventProducers)
						logger.error("Could not find the Event Producer with address: "
								+ address);
					continue;
				}

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
			for (MocaTraceType currentTraceType : activeTypes) {
				if (currentTraceType == MocaTraceType.VIRTUAL_ADDRESSING
						|| currentTraceType == MocaTraceType.PHYSICAL_ADDRESSING) {
					currentEP.put(currentTraceType, root.get(currentTraceType));
					continue;
				}
				
				// Update the task event producer
				EventProducer ep = taskProducersIndex.get(currentTraceType).get("task" + fields[1]).get("task" + fields[1]);
				ep.setName(fields[MocaConstants.T_PID]);
				ep.setType(fields[MocaConstants.ENTITY]);
				ep.setLocalId(String.valueOf(ep.getId()));
				currentTask = "task" + fields[1];
				
				currentEP.put(currentTraceType, ep);
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
			allProducers.put(aTraceType, new LinkedList<EventProducer>());
			eventList.put(aTraceType, new LinkedList<Event>());
			ignoredEventProd.put(aTraceType, new LinkedList<Long>());
			consecutiveProducers.put(aTraceType, new LinkedList<List<EventProducer>>());
			producersIndex.put(aTraceType,new HashMap<String, EventProducer>());
			taskProducersIndex.put(aTraceType, new HashMap<String, Map<String, EventProducer>>());
		}
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

		long previousAddress = -1;
		LinkedList<EventProducer> currentConsecutiveProducers = new LinkedList<EventProducer>();
		LinkedList<EventProducer> newEventProd = new LinkedList<EventProducer>();
		HashMap<String, List<String>> producerTaskIndex = new HashMap<String, List<String>>();
		BufferedReader br;
		try {
			br = new BufferedReader(new InputStreamReader(new DataInputStream(
					new FileInputStream(aTraceFile))));

			String[] line;
			// First line should be memory page size
			line = getLine(br);
			memoryPageSize = Integer.valueOf(line[0]);
			
			// Init to a value we are sure is not consecutive to the first address
			previousAddress = memoryPageSize - 1;
			
			while ((line = getLine(br)) != null) {
				if (line.length <= 1)
					continue;

				long addr = Long.valueOf(line[0], 16);

				// Create a producer with root pid
				EventProducer anEP = createProducer(addr,
						root.get(currentTraceType).getId());
						
				if (currentTraceType == MocaTraceType.VIRTUAL_ADDRESSING
						|| currentTraceType == MocaTraceType.PHYSICAL_ADDRESSING) {
					newEventProd.add(anEP);
				}
				
				// Is it consecutive?
				if (previousAddress + memoryPageSize == addr) {
					// Then add to current list
					currentConsecutiveProducers.add(anEP);
				} else if (!currentConsecutiveProducers.isEmpty()) {
					// Else save the current list
					consecutiveProducers.get(currentTraceType).add(
							currentConsecutiveProducers);
					// Start a new list
					currentConsecutiveProducers = new LinkedList<EventProducer>();
					// And add the current prod
					currentConsecutiveProducers.add(anEP);
				} else {
					// The current list is empty, just add the EP
					currentConsecutiveProducers.add(anEP);
				}
				
				
				for (int i = 1; i < line.length; i++) {
					if(!producerTaskIndex.containsKey(line[i]))
						producerTaskIndex.put(line[i], new LinkedList<String>());
					
					producerTaskIndex.get(line[i]).add(String.valueOf(addr));
				}
				if (line.length > 2)
					sharedAddress.add(addr);
				
				// Update the previous address
				previousAddress = addr;
			}
			
			// Save the last producer(s)
			consecutiveProducers.get(currentTraceType).add(
							currentConsecutiveProducers);
			
			buildHierarchy(currentTraceType);
			createTaskEventProd(producerTaskIndex, virtual);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Expand the hierarchy for the current trace type
	 * @param aTraceType
	 */
	private void buildHierarchy(MocaTraceType aTraceType) {
		logger.debug(aTraceType.toString());

		for (List<EventProducer> groupOfProducers : consecutiveProducers
				.get(aTraceType)) {
			// If only one EP in the group
			if (groupOfProducers.size() == 1) {
				if (trimLoneEventProducers) {
					// Ignore it
					ignoredEventProd.get(aTraceType).add(Long.valueOf(groupOfProducers.get(0).getName()));
					continue;
				} else {
					// Just add it to the list
					allProducers.get(aTraceType).addAll(groupOfProducers);
				}
			} else {
				// Expand hierarchy
				int dividingFactor = findDividingFactor(groupOfProducers);
				if (dividingFactor > 0) {
					allProducers.get(aTraceType).addAll(
							createHierarchy(groupOfProducers, 0.0,
									root.get(aTraceType).getId(),
									dividingFactor));
				} else {
					allProducers.get(aTraceType).addAll(groupOfProducers);
				}
			}
		}
		
		// Build Index
		for (EventProducer aProd : allProducers.get(aTraceType))
			producersIndex.get(aTraceType).put(aProd.getName(), aProd);
	}
	
	/**
	 * Recursively expand the hierarchy tree, following a top-down approach, by
	 * creating new event producers by merging consecutive EP
	 * 
	 * @param eventProdToMerge
	 *            the list of event producer that are consecutive
	 * @param currentHierarchyDepth
	 *            the current depth we are building in the hierarchy tree
	 * @param ppid
	 *            the parent id of the created nodes
	 * @param dividingFactor
	 *            the factor into which the result will be divided
	 * @return the list of created event producers
	 */
	private List<EventProducer> createHierarchy(
			List<EventProducer> eventProdToMerge, double currentHierarchyDepth,
			int ppid, int dividingFactor) {
		LinkedList<EventProducer> newEventProd = new LinkedList<EventProducer>();

		// Sort by ascending addresses
		Collections.sort(eventProdToMerge, new Comparator<EventProducer>() {
			@Override
			public int compare(final EventProducer arg0,
					final EventProducer arg1) {
				return arg0.getName().compareTo(arg1.getName());
			}
		});

		int groupSize;
		
		// If first hierarchy depth
		if (currentHierarchyDepth == 0.0)
			// Do not split, just create a super producer representing the whole
			// group
			groupSize = eventProdToMerge.size();
		else
			// Compute the size of a new group
			groupSize = eventProdToMerge.size() / dividingFactor;

		if (groupSize <= 1) 
			return eventProdToMerge;
		
		int mergedProducers = 0;
		int i;

		// Compute new group of EP
		for (i = 0; i < eventProdToMerge.size() - groupSize; i = i + groupSize) {
			EventProducer newNode = createProducer(eventProdToMerge.get(i)
					.getName() + "_" + (int) currentHierarchyDepth, ppid);
			newEventProd.add(newNode);
			LinkedList<EventProducer> newSubGroup = new LinkedList<EventProducer>();

			// Update the parent of leaves event prod
			for (int j = i; j < i + groupSize; j++) {
				eventProdToMerge.get(j).setParentId(newNode.getId());
				newSubGroup.add(eventProdToMerge.get(j));
			}

			// Keep merging?
			if (currentHierarchyDepth + 1 < maxHierarchyDepth
					&& newSubGroup.size() >= dividingFactor
					&& newSubGroup.size() > 1) {
				newEventProd.addAll(createHierarchy(newSubGroup,
						currentHierarchyDepth + 1, newNode.getId(),
						dividingFactor));
			}
			mergedProducers = i + groupSize;
		}

		int remainingEP = eventProdToMerge.size() - mergedProducers;
		if (remainingEP == 1) {
			newEventProd.add(eventProdToMerge.get(eventProdToMerge.size() - 1));
		} else
		// Check if some producer remains
		if (mergedProducers < eventProdToMerge.size()) {
			EventProducer newNode = createProducer(eventProdToMerge.get(i)
					.getName() + "_" + (int) currentHierarchyDepth, ppid);
			newEventProd.add(newNode);
			LinkedList<EventProducer> newSubGroup = new LinkedList<EventProducer>();

			for (i = mergedProducers; i < eventProdToMerge.size(); i++) {
				eventProdToMerge.get(i).setParentId(newNode.getId());
				newSubGroup.add(eventProdToMerge.get(i));
			}

			if (currentHierarchyDepth + 1 < maxHierarchyDepth
					&& newSubGroup.size() >= dividingFactor
					&& newSubGroup.size() > 1 && dividingFactor > 1) {
				newEventProd.addAll(createHierarchy(newSubGroup,
						currentHierarchyDepth + 1, newNode.getId(),
						dividingFactor));
			} else {
				newEventProd.addAll(newSubGroup);
			}
		}

		logger.debug(currentHierarchyDepth + ", " + newEventProd.size());
		return newEventProd;
	}

	/**
	 * Given the size of a group of event producers and the depth of the
	 * hierarchy we want to achieve, find the dividing factor for the group of
	 * EP such that we have a regular hierarchy tree
	 * 
	 * @param groupOfEP
	 *            the group of EP to merge
	 * @return the found dividing factor
	 */
	private int findDividingFactor(List<EventProducer> groupOfEP) {
		int dividingFactor = 0;

		// Try the first 10000 integer
		for (int i = 1; i <= 10000; i++) {
			if (Math.pow(i, maxHierarchyDepth) > groupOfEP.size()) {
				break;
			}
			dividingFactor++;
		}

		// If we only found 1 as solution try to see if with the value 2, we
		// can find a working solution with a smaller hierarchy depth (which
		// means we will not necessarily reach the wanted maxdepth for this
		// group
		if (dividingFactor == 1) {
			// Try the first 10000 integer
			for (int i = maxHierarchyDepth; i > 1; i--) {
				if (Math.pow(2, i) < groupOfEP.size()) {
					dividingFactor = 2;
					break;
				}
			}
		}

		return dividingFactor;
	}

	private void createTaskEventProd(
			HashMap<String, List<String>> producerTaskIndex, boolean virtual) {
		MocaTraceType currentTraceType;
		MocaTraceType nonTaskTraceType;
		if (!virtual) {
			currentTraceType = MocaTraceType.TASK_PHYSICAL_ADDRESSING;
			nonTaskTraceType = MocaTraceType.PHYSICAL_ADDRESSING;
		} else {
			currentTraceType = MocaTraceType.TASK_VIRTUAL_ADDRESSING;
			nonTaskTraceType = MocaTraceType.VIRTUAL_ADDRESSING;
		}

		for (String aTask : producerTaskIndex.keySet()) {
			EventProducer taskProducer = createProducer(aTask,
					root.get(currentTraceType).getId());
			taskProducer.setType("Task Producer");
			allProducers.get(currentTraceType).add(taskProducer);
			// Index keeping track of the correspondence between ancient and new
			// producers
			HashMap<Integer, EventProducer> parentIndex = new HashMap<Integer, EventProducer>();
			List<EventProducer> newProducers = new LinkedList<EventProducer>();
			taskProducersIndex.get(currentTraceType).put(aTask,
					new HashMap<String, EventProducer>());
			taskProducersIndex.get(currentTraceType).get(aTask)
					.put(taskProducer.getName(), taskProducer);

			// Create the producer
			for (EventProducer aProd : allProducers.get(nonTaskTraceType)) {
				// Don't copy root
				if (!aProd.getName().equals(MocaConstants.MEMORY_ROOT_NAME)) {
					EventProducer newProducer = createProducer(aProd.getName(),
							aProd.getParentId());
					parentIndex.put(aProd.getId(), newProducer);
					newProducers.add(newProducer);

				} else {
					parentIndex.put(root.get(nonTaskTraceType).getId(),
							taskProducer);
				}
			}

			// Set the parent ID
			for (EventProducer aProd : newProducers) {
				// Don't update root
				if (!aProd.getName().equals(MocaConstants.TASK_ROOT_NAME)
						&& !aProd.getType().equals("Task Producer")) {

					if (parentIndex.containsKey(aProd.getParentId())) {
						aProd.setParentId(parentIndex.get(aProd.getParentId())
								.getId());
					} else {
						logger.error("Missing parent ID while rebuilding the hierarchy in process "
								+ aTask);
					}
				}
			}

			for (EventProducer aProd : newProducers)
				taskProducersIndex.get(currentTraceType).get(aTask)
						.put(aProd.getName(), aProd);

			allProducers.get(currentTraceType).addAll(newProducers);
		}
	}
	
}
