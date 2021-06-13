/*
 * Copyright 2012, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.dexlib2.dexbacked;


import com.google.common.collect.ImmutableList;
import org.jf.dexlib2.dexbacked.instruction.DexBackedInstruction;
import org.jf.dexlib2.dexbacked.raw.CodeItem;
import org.jf.dexlib2.dexbacked.util.DebugInfo;
import org.jf.dexlib2.dexbacked.util.FixedSizeList;
import org.jf.dexlib2.dexbacked.util.VariableSizeListIterator;
import org.jf.dexlib2.dexbacked.util.VariableSizeLookaheadIterator;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.debug.DebugItem;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.util.AlignmentUtils;
import org.jf.util.ExceptionWithContext;

import java.util.Iterator;
import java.util.List;

public class DexBackedMethodImplementation implements MethodImplementation {

    public final DexBackedDexFile dexFile;

    public final DexBackedMethod method;
    private final int codeOffset;

    public DexBackedMethodImplementation(DexBackedDexFile dexFile,
                                         DexBackedMethod method,
                                         int codeOffset) {
        this.dexFile = dexFile;
        this.method = method;
        this.codeOffset = codeOffset;
    }

    @Override
    public int getRegisterCount() {
        return dexFile.readUshort(codeOffset);
    }


    @Override
    public Iterable<? extends Instruction> getInstructions() {
        // instructionsSize is the number of 16-bit code units in the instruction list, not the number of instructions
        int instructionsSize = dexFile.readSmallUint(codeOffset + CodeItem.INSTRUCTION_COUNT_OFFSET);

        final int instructionsStartOffset = codeOffset + CodeItem.INSTRUCTION_START_OFFSET;
        final int endOffset = instructionsStartOffset + (instructionsSize * 2);
        return new Iterable<Instruction>() {
            @Override
            public Iterator<Instruction> iterator() {
                return new VariableSizeLookaheadIterator<Instruction>(dexFile, instructionsStartOffset) {
                    @Override
                    protected Instruction readNextItem(DexReader reader) {
                        if (reader.getOffset() >= endOffset) {
                            return endOfData();
                        }

                        Instruction instruction = DexBackedInstruction.readFrom(reader);

                        // Does the instruction extend past the end of the method?
                        int offset = reader.getOffset();
                        if (offset > endOffset || offset < 0) {
                            throw new ExceptionWithContext("The last instruction in method %s is truncated", method);
                        }
                        return instruction;
                    }
                };
            }
        };
    }


    @Override
    public List<? extends DexBackedTryBlock> getTryBlocks() {
        final int triesSize = dexFile.readUshort(codeOffset + CodeItem.TRIES_SIZE_OFFSET);
        if (triesSize > 0) {
            int instructionsSize = dexFile.readSmallUint(codeOffset + CodeItem.INSTRUCTION_COUNT_OFFSET);
            final int triesStartOffset = AlignmentUtils.alignOffset(
                    codeOffset + CodeItem.INSTRUCTION_START_OFFSET + (instructionsSize * 2), 4);
            final int handlersStartOffset = triesStartOffset + triesSize * CodeItem.TryItem.ITEM_SIZE;

            return new FixedSizeList<DexBackedTryBlock>() {

                @Override
                public DexBackedTryBlock readItem(int index) {
                    return new DexBackedTryBlock(dexFile,
                            triesStartOffset + index * CodeItem.TryItem.ITEM_SIZE,
                            handlersStartOffset);
                }

                @Override
                public int size() {
                    return triesSize;
                }
            };
        }
        return ImmutableList.of();
    }


    private DebugInfo getDebugInfo() {
        int debugOffset = dexFile.readInt(codeOffset + CodeItem.DEBUG_INFO_OFFSET);

        if (debugOffset == -1 || debugOffset == 0) {
            return DebugInfo.newOrEmpty(dexFile, 0, this);
        }
        if (debugOffset < 0) {
            System.err.println(String.format("%s: Invalid debug offset", method));
            return DebugInfo.newOrEmpty(dexFile, 0, this);
        }
        if (debugOffset >= dexFile.buf.length) {
            System.err.println(String.format("%s: Invalid debug offset", method));
            return DebugInfo.newOrEmpty(dexFile, 0, this);
        }
        return DebugInfo.newOrEmpty(dexFile, debugOffset, this);
    }


    @Override
    public Iterable<? extends DebugItem> getDebugItems() {
        return getDebugInfo();
    }


    public Iterator<String> getParameterNames(DexReader dexReader) {
        return getDebugInfo().getParameterNames(dexReader);
    }

    /**
     * Calculate and return the private size of a method implementation.
     * <p>
     * Calculated as: debug info size + instructions size + try-catch size
     *
     * @return size in bytes
     */
    public int getSize() {
        int debugSize = getDebugInfo().getSize();

        //set last offset just before bytecode instructions (after insns_size)
        int lastOffset = codeOffset + CodeItem.INSTRUCTION_START_OFFSET;

        //set code_item ending offset to the end of instructions list (insns_size * ushort)
        lastOffset += dexFile.readSmallUint(codeOffset + CodeItem.INSTRUCTION_COUNT_OFFSET) * 2;

        //read any exception handlers and move code_item offset to the end
        for (DexBackedTryBlock tryBlock : getTryBlocks()) {
            Iterator<? extends DexBackedExceptionHandler> tryHandlerIter =
                    tryBlock.getExceptionHandlers().iterator();
            while (tryHandlerIter.hasNext()) {
                tryHandlerIter.next();
            }
            lastOffset = ((VariableSizeListIterator) tryHandlerIter).getReaderOffset();
        }

        //method impl size = debug block size + code_item size
        return debugSize + (lastOffset - codeOffset);
    }
}