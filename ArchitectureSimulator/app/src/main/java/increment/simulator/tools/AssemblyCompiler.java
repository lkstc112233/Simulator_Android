package increment.simulator.tools;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import increment.simulator.util.ConvenientStreamTokenizer;
import static increment.simulator.util.ExceptionHandling.panic;
/**
 * A program that compiles a program for this architecture.
 * @author Xu Ke
 *
 */
public class AssemblyCompiler {
	/**
	 * A compiled, append only program. Basically it's a Short array. You can iterate through it by using for-each loop. 
	 * @author Xu Ke
	 *
	 */
	public static class CompiledProgram implements Iterable<Short>{
		/**
		 * Storage.
		 */
		private List<Short> program = new ArrayList<>();
		/**
		 * Adds a new instruction.
		 * @param inst
		 */
		public void addInstruction(short inst) {
			program.add(inst);
		}
		/**
		 * Provides support for <b>for-each</b> loop.
		 */
		@Override
		public Iterator<Short> iterator() {
			return program.iterator();
		}	
	}
	/**
	 * Compiles a stream-written source program.
	 * @param source
	 * @return
	 */
	public static CompiledProgram compile(Reader source) {
		// Create a tokenizer, either by file or by text.
		ConvenientStreamTokenizer tokens = null;
		tokens = new ConvenientStreamTokenizer(source);
		// Compile the program and return.
		CompiledProgram program = null;
		try {
			program = compile(tokens);
		} catch (IOException e) {
			System.err.println("File format error.");
			System.exit(-1);
		}
		return program;
	}
	/**
	 * Alias for text source.
	 * @param source
	 * @return
	 */
	public static CompiledProgram compile(String source) {
		return compile(new StringReader(source));
	}
	/**
	 * Compile!
	 * @param tokens
	 * @return
	 * @throws IOException
	 */
	private static CompiledProgram compile(ConvenientStreamTokenizer tokens) throws IOException{
		CompiledProgram program = new CompiledProgram();
		int instruction;
		while ((instruction = phaseInstruction(tokens)) >= 0)
				program.addInstruction((short) instruction);
		return program;
	}
	/**
	 * Parse instruction.
	 * @param tokens
	 * @return parsed instruction. <br>-1 when unrecognized.
	 * @throws IOException When needed.
	 */
	private static int phaseInstruction(ConvenientStreamTokenizer tokens) throws IOException {
		int token = tokens.nextToken();
		if (token != ConvenientStreamTokenizer.TT_WORD && token != ConvenientStreamTokenizer.TT_NUMBER)
			return -1;
		if (token == ConvenientStreamTokenizer.TT_NUMBER)
			return (int) tokens.nval;
		switch(tokens.sval){
		case "LDR": // 0x01
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (1 << 10));
		case "STR": // 0x02 
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (2 << 10));
		case "LDA": // 0x03 
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (3 << 10));
		case "LDX": // 0x21 
			return (parseIXAndAddressAndOptionalI(tokens) | (33 << 10));
		case "STX": // 0x22
			return (parseIXAndAddressAndOptionalI(tokens) | (34 << 10));
		case "JZ": // 0x08
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (8 << 10));
		case "JNE": // 0x09
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (9 << 10));
		case "JCC": // 0x0A
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (10 << 10));
		case "JMA": // 0x0B
			return (parseIXAndAddressAndOptionalI(tokens) | (11 << 10));
		case "JSR": // 0x0C
			return (parseIXAndAddressAndOptionalI(tokens) | (3 << 8) | (12 << 10)); // This is a trick.
		case "RFS": // 0x0D
			return (parseImmediate(tokens) | (3 << 8) | (13 << 10)); // Same trick here.
		case "SOB": // 0x0E
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (14 << 10));
		case "JGE": // 0x0F
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (15 << 10));
		case "AMR": // 0x04
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (4 << 10));
		case "SMR": // 0x05
			return (parseRAndIXAndAddressAndOptionalI(tokens) | (5 << 10));
		case "AIR": // 0x06
			return (parseRAndImmediate(tokens) | (6 << 10));
		case "SIR": // 0x07
			return (parseRAndImmediate(tokens) | (7 << 10));
		case "MLT": // 0x10
			return (parseRxAndRy(tokens) | (16 << 10));
		case "DVD": // 0x11
			return (parseRxAndRy(tokens) | (17 << 10));
		case "TRR": // 0x12
			return (parseRxAndRy(tokens) | (18 << 10));
		case "AND": // 0x13
			return (parseRxAndRy(tokens) | (19 << 10));
		case "ORR": // 0x14
			return (parseRxAndRy(tokens) | (20 << 10));
		case "NOT": // 0x15
			return (parseRx(tokens) | (21 << 10));
		case "IN":  // 0x31
			return (parseRAndImmediate(tokens) | (49 << 10));
		case "OUT": // 0x32
			return (parseRAndImmediate(tokens) | (50 << 10));
		case "SRC": // 0x19
			return (parseRAndCountAndLRAndAL(tokens) | (25 << 10));
		case "RRC": // 0x1A
			return (parseRAndCountAndLRAndAL(tokens) | (26 << 10));
		case "CHK":
			return (parseRAndImmediate(tokens) | (51 << 10));
		case "HLT": // 0
			return 0;
		case "NOP": // 0x3F, does nothing.
			return (63 << 10);
		}
		panic("Unrecognized instruction:" + tokens.sval);
		return -1;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseRAndIXAndAddressAndOptionalI(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		if (tokens.nextToken() != ',')
			panic("Unexpected token at line " + tokens.lineno());
		i <<= 8;
		i |= parseIXAndAddressAndOptionalI(tokens);
		return i;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseIXAndAddressAndOptionalI(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		if (tokens.nextToken() != ',')
			panic("Unexpected token at line " + tokens.lineno());
		i <<= 6;
		i |= parseAddressAndOptionalI(tokens);
		return i;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseAddressAndOptionalI(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		i |= parseOptionalI(tokens);
		return i;
	}
	/**
	 * Parses I part in an instruction at bit-5
	 * @param tokens
	 * @return 0 - if there is no I part or I part is 0. <br> 32 - if I part is 1.
	 * @throws IOException
	 */
	private static short parseOptionalI(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ',') {
			tokens.pushBack();
			return 0;
		}
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		if (1 == (int)tokens.nval)
			return 1 << 5;
		return 0;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseRAndImmediate(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		if (tokens.nextToken() != ',')
			panic("Unexpected token at line " + tokens.lineno());
		i <<= 8;
		i |= parseImmediate(tokens);
		return i;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseImmediate(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		return (short) tokens.nval;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseRxAndRy(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		if (tokens.nextToken() != ',')
			panic("Unexpected token at line " + tokens.lineno());
		i <<= 8;
		i |= parseRy(tokens);
		return i;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseRx(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		return (short) ((int)tokens.nval << 8);
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseRy(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		return (short) ((int)tokens.nval << 6);
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseRAndCountAndLRAndAL(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		if (tokens.nextToken() != ',')
			panic("Unexpected token at line " + tokens.lineno());
		i <<= 8;
		i |= parseCountAndLRAndAL(tokens);
		return i;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseCountAndLRAndAL(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		if (tokens.nextToken() != ',')
			panic("Unexpected token at line " + tokens.lineno());
		i |= parseLRAndAL(tokens);
		return i;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseLRAndAL(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		short i = (short) tokens.nval;
		if (tokens.nextToken() != ',')
			panic("Unexpected token at line " + tokens.lineno());
		i <<= 6;
		i |= parseAL(tokens);
		return i;
	}
	/**
	 * Parse parameters.
	 * @param tokens
	 * @return parsed parameters.
	 * @throws IOException
	 * @throws IllegalStateException when file is corrupted.
	 */
	private static short parseAL(ConvenientStreamTokenizer tokens) throws IOException {
		if (tokens.nextToken() != ConvenientStreamTokenizer.TT_NUMBER)
			panic("Unexpected token at line " + tokens.lineno());
		return (short) ((int)tokens.nval << 7);
	}
	/**
	 * Main function for testing.
	 * @param args
	 */
	public static void main(String[] args) {
		CompiledProgram cpm = null;
		try {
			cpm = compile(new FileReader(args[0]));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(args[1]);
			for (short s : cpm.program) {
				System.out.println(s);
				out.write(s >> 8);
				out.write(s);
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
