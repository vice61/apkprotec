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

package org.jf.dexlib2.iface;


import java.util.List;

/**
 * This class represents an individual try block and associated set of handlers.
 */
public interface TryBlock<EH extends ExceptionHandler> {
    /**
     * Gets the code offset of the start of this try block.
     * <p>
     * The starting location must not occur in the middle of an instruction.
     *
     * @return The offset of the start of the try block from the the beginning of the bytecode for the method. The
     * offset will be in terms of 16-bit code units.
     */
    int getStartCodeAddress();

    /**
     * Gets the number of code units covered by this try block.
     * <p>
     * The end of the try block is typically coincident with the end of an instruction, but does not strictly need to
     * be. If the last instruction is only partly covered by this try block, it is considered to be covered.
     *
     * @return The number of code units covered by this try block.
     */
    int getCodeUnitCount();

    /**
     * A list of the exception handlers associated with this try block.
     * <p>
     * The exception handlers in the returned list will all have a unique type, including at most 1 with no type, which
     * is the catch-all handler. If present, the catch-all handler is always the last item in the list.
     *
     * @return A list of ExceptionHandler objects
     */

    List<? extends EH> getExceptionHandlers();

    /**
     * Compares this TryBlock to another TryBlock for equality.
     * <p>
     * This TryBlock is equal to another TryBlock if all 3 fields are equal. The exception handlers are tested for
     * equality using the usual List equality semantics.
     *
     * @param o The object to be compared for equality with this TryBlock
     * @return true if the specified object is equal to this TryBlock
     */
    @Override
    boolean equals(Object o);
}
