package com.photoncat.architecturesimulator.simulator.chips;

import com.photoncat.architecturesimulator.simulator.Cable;
import com.photoncat.architecturesimulator.simulator.SingleCable;
import com.photoncat.architecturesimulator.simulator.tools.AssemblyCompiler.CompiledProgram;

/**
 * The memory. It's abstracted as a black box that supports read/load by byte, a.k.a. a memory interface.<br>
 * There is a build in cache inside this interface. It's a magical cache! implemented by MAGIC!<br>
 * A memory will have three inputs:<br>
 * 		* load[1]<br>
 * 		* address[12]<br>
 * 		* input[16] <br>
 * A memory will have one output:<br>
 * 		* output[16]
 * 
 * @author Xu Ke
 *
 */
public class Memory extends Chip {
	/** 
	 * Memory data stored in a big array. Using cable allow me to handle bits more conveniently. 
	 */
	protected Cable[] data;
	protected static class CacheEntry {
		public int tag;
		public boolean valid = false;
	}
	protected CacheEntry[] cache;
	protected int cachePointer = 0;
	/**
	 * Constructor. Creating a 12-bit addressed memory (4096 words, addressing from 0 to 4095).
	 */
	public Memory(){
		this(12);
	}
	/**
	 * Constructor. Creating a width-bit addressed memory.
	 * @param width
	 */
	public Memory(int width) {
		data = new Cable[1 << width];
		changed = new boolean[1 << width];
		for (int i = 0; i < data.length; ++i)
			data[i] = new SingleCable(16);
		addPort("load", 1);
		addPort("address", 1 << width);
		addPort("input", 16);
		addPort("output", 16);
		cache = new CacheEntry[16];
		for (int i = 0; i < 16; ++i)
			cache[i] = new CacheEntry();
	}
	/**
	 * When timer ticks, if input[0] is true, we move data of input to data[address].
	 */
	@Override
	public void tick(){
		if (getPort("load").getBit(0)) {
			int address = (int) getPort("address").toInteger();
			if (outofRange(address))
				return; // Or throw.
			data[address].assign(getPort("input"));
			changed[address] = true;
		}
		loadCache((int)getPort("address").toInteger() >> 2);
	}
	/**
	 * Checks if target address is out of range.
	 * @param address
	 * @return
	 */
	private boolean outofRange(int address) {
		if (address < 0)
			return true;
		return address >= data.length;
	}
	/**
	 * When evaluates, we move specified data to output.
	 * @return true if value changed.
	 */
	@Override
	public boolean evaluate(){
		int address = (int) getPort("address").toInteger();
		if (outofRange(address))
			return false; // Or throw.
		return getPort("output").assign(data[address]);
	}
	
	/**
	 * Only those data assigned will be output during toString.
	 * This array is used to store if those bits are assigned.
	 */
	protected boolean[] changed;
	/**
	 * Turns chip value into a readable way.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Cache Status:\n");
		for (int i = 0; i < 16; ++i) {
			if (!cache[i].valid)
				continue;
			int tag = cache[i].tag;
			sb.append("Tag ");
			sb.append(tag);
			sb.append(": \n\t");
			for (int j = tag << 2; j < (cache[i].tag << 2) + 4; ++j) {
				sb.append(String.format("%04X", data[j].toInteger()));
				sb.append(" ");
			}
			sb.append("\n");
		}
		sb.append("Memory chip data:\n");
		for (int i = 0; i < data.length; ++i) {
			if (!changed[i]) continue;
			sb.append(i);
			sb.append(": ");
			sb.append(data[i].toInteger());
			sb.append("\n");
		}
		return sb.toString();
	}
	/**
	 * Put value to desired address.
	 * @param address
	 * @param value
	 */
	public void putValue(int address, int value) {
		data[address].putValue(value);
		changed[address] = true;
	}
	/**
	 * Load a program into memory.
	 * @param address The starting address in memory. 
	 * @param code The compiled program. {@link com.photoncat.architecturesimulator.simulator.tools.AssemblyCompiler}
	 */
	public void loadProgram(int address, CompiledProgram code) {
		for (Short ins : code) {
			putValue(address++, ins);
		}
	}
	private void loadCache(int tag) {
		for (int i = 0; i < 16; ++i)
			if (cache[i].valid)
				if (cache[i].tag == tag)
					return;
		cache[cachePointer].valid = true;
		cache[cachePointer].tag = tag;
		cachePointer++;
		cachePointer %= 16;
	}
}
