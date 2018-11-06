package net.fornwall.jelf;

import java.io.IOException;

public class ElfRelocation {

    private final ElfFile elfHeader;
    private final SymbolLocator symtab;

    final long offset;
    private final long info;
    private final long addend;

    ElfRelocation(ElfParser parser, long offset, long entry_size, SymbolLocator symtab) {
        this.elfHeader = parser.elfFile;
        this.symtab = symtab;

        parser.seek(offset);

        if (parser.elfFile.objectSize == ElfFile.CLASS_32) {
            this.offset = parser.readInt() & 0xffffffffL;
            this.info = parser.readInt();
            this.addend = entry_size >= 12 ? parser.readInt() : 0;
        } else {
            this.offset = parser.readLong();
            this.info = parser.readLong();
            this.addend = entry_size >= 24 ? parser.readLong() : 0;
        }
    }

    public long offset() {
        return offset;
    }

    public ElfSymbol symbol() throws IOException {
        int mask = elfHeader.objectSize == ElfFile.CLASS_32 ? 8 : 32;
        return symtab.getELFSymbol((int) (info >>> mask));
    }

    public int type() {
        int mask = elfHeader.objectSize == ElfFile.CLASS_32 ? 0xff : 0xffffffff;
        return (int) (info & mask);
    }

    public long addend() {
        return addend;
    }

}
