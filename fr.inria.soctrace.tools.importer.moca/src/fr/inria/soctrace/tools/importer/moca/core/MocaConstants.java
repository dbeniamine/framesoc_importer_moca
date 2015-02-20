/*******************************************************************************
 * Copyright (c) 2012-2014 Generoso Pagano.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Generoso Pagano - initial API and implementation
 *     David Beniamine - Adaptation from PjDump to HeapInfo
 ******************************************************************************/
package fr.inria.soctrace.tools.importer.moca.core;

/**
 * Constants for Moca parser
 * 
 * @author "David Beniamine <David.Beniamine@imag.fr>"
 */
public class MocaConstants {

	/**
	 * Field separator
	 */
	public static final String SEPARATOR = " ";

	/**
	 * Page size
	 */
	public static final int DB_PAGE_SIZE = 20000;

	/**
	 * Trace Type name
	 */
	public static final String TRACE_TYPE = "Moca";
	public static final String TRACE_EXT = ".log";
	public static final String TRACE_FILE_TYPE = "Moca-task";

	/**
	 * Entity labels
	 */
	public static final String TASK = "Task";
	public static final String CHUNK = "Chunk";
	public static final String ACCESS = "Access";
	
	// Task ID PID
	public static final int TASK_NAME = 0;
	public static final int T_ID = 1;
	public static final int T_PID = 2;
	public static final int MEM_PAGE_SIZE = 3;
	public static final String TASK_ZERO = "0";
	

	/** Line positions for interesting columns */
	public static final int ENTITY = 0;

	// Chunk ID Size Start End CPUMask
	public static final int C_Name = 1;
	public static final int C_Size = 2;
	public static final int C_Start = 3;
	public static final int C_End = 4;
	public static final int C_ThreadMask = 5;

	public static final int C_Param_offset = 2;
	public static final int C_Param_Addr = 0;
	public static final int C_Param_Size = 1;
	public static final int C_Param_ThreadMask = 2;

	/** New Struct event type and params */
	public static final String NEW_CHUNK_EVENT = "New_Chunk";

	public static final String[] NEW_CHUNK_PARAM = { "Size", "Start", "End",
			"CPUMask" }; 
	public static final String[] NEW_CHUNK_PARAM_TYPES = { "INTEGER", "INTEGER",
			"INTEGER", "INTEGER" };

	// Access AddrVirtual AddrPhysique Start End CPUMask
	public static final int A_VirtAddr = 1;
	public static final int A_PhysAddr = 2;
	public static final int A_NReads = 3;
	public static final int A_NWrites = 4;
	public static final int A_CPUMask = 5;


	public static final int A_Param_offset = 3;
	public static final int A_Param_Addr = 0;
	public static final int A_Param_Size = 1;
	public static final int A_Param_ThreadMask = 2;

	// Access types Read/Write and private/shared, these must correspond to the
	// entries of ACCESS_EVENTS
	public static final int A_Type_Read = 0;
	public static final int A_Type_Write = 1;
	public static final int A_Type_Private = 0;
	public static final int A_Type_Shared = 2;

	/** Access event type and params */
	public static final String[] ACCESS_EVENTS = { "Private Read",
			"Private Write", "Shared Read", "Shared Write" };

	public static final String[] ACCESS_PARAM = { "Addr", "Size", "CPUMask" };
	public static final String[] ACCESS_PARAM_TYPES = { "INTEGER", "INTEGER",
			"STRING", "INTEGER", "INTEGER" };

	// Root name
	public static final String TASK_ROOT_NAME = "Moca Launcher";
	public static final String MEMORY_ROOT_NAME = "MemoryRoot";	
	
	/**
	 * Time shift exponent (nanoseconds)
	 */
	public static final int TIME_SHIFT = 9;
	
	/**
	 * Enumerate the different types of traces
	 */
	public static enum MocaTraceType {
		TASK_VIRTUAL_ADDRESSING, TASK_PHYSICAL_ADDRESSING, VIRTUAL_ADDRESSING, PHYSICAL_ADDRESSING
	};

}
