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
	public static final String SEPARATOR = ",";

	/**
	 * Page size
	 */
	public static final int DB_PAGE_SIZE = 20000;

	/**
	 * Trace Type name
	 */
	public static final String TRACE_TYPE = "Moca";
	public static final String TRACE_EXT = ".csv";
	public static final String TRACE_FILE_TYPE = "Moca-full-trace";
	
	public static final String STRUCT_FILE_TYPE = "structs.csv";
	public static final String STACK_FILE_TYPE = "stackmap.csv";

	// Access AddrVirtual AddrPhysique Start End CPUMask
	// @Virt, @Phy, Nreads, Nwrites, CPUMask, Start, End, TaskId
	public static final int A_VirtAddr = 0;
	public static final int A_PhysAddr = 1;
	public static final int A_NReads = 2;
	public static final int A_NWrites = 3;
	public static final int A_CPUMask = 4;
	public static final int A_Start = 5;
	public static final int A_End = 6;
	public static final int A_Tid = 7;



	public static final int A_Param_offset = 3;
	public static final int A_Param_Addr = 0;
	public static final int A_Param_Size = 1;
	public static final int A_Param_ThreadMask = 2;

	// Access types Read/Write and private/shared, these must correspond to the
	// entries of ACCESS_EVENTS
	public static final int A_Type_Read = 0;
	public static final int A_Type_Write = 1;


	/** Access event type and params */
	public static final String[] ACCESS_EVENTS = { "Read", "Write" };

	public static final String[] ACCESS_PARAM = { "Addr", "Size", "CPUMask" };
	public static final String[] ACCESS_PARAM_TYPES = { "INTEGER", "INTEGER",
			"STRING", "INTEGER", "INTEGER" };

	// Root name
	public static final String MEMORY_ROOT_NAME = "MemoryRoot";	
	
	public static final String STACK_NAME= "Stack#";
	// Structs / Stacks format
	public static final int S_NAME=0;
	public static final int S_ADDR=1;
	public static final int S_SIZE=2;
	
	/**
	 * Time shift exponent (nanoseconds)
	 */
	public static final int TIME_SHIFT = 9;
	
	/**
	 * Enumerate the different types of traces
	 */
	public static enum MocaTraceType {
		VIRTUAL_ADDRESSING, PHYSICAL_ADDRESSING
	};

}
