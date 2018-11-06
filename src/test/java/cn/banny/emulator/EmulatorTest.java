package cn.banny.emulator;

import junit.framework.TestCase;

public abstract class EmulatorTest extends TestCase {

    protected Emulator emulator;

    private long start;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        start = System.currentTimeMillis();
        emulator = EmulatorFactory.createARMEmulator();
        emulator.getMemory().setLibraryResolver(createLibraryResolver());
    }

    protected abstract LibraryResolver createLibraryResolver();

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        emulator.close();
        System.err.println("test offset=" + (System.currentTimeMillis() - start) + "ms");
    }
}
