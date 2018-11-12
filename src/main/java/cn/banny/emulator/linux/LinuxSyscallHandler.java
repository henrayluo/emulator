package cn.banny.emulator.linux;

import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.Emulator;
import cn.banny.emulator.SvcMemory;
import cn.banny.emulator.SyscallHandler;
import cn.banny.emulator.arm.ARM;
import cn.banny.emulator.dlfcn.Dlfcn;
import cn.banny.emulator.linux.file.*;
import cn.banny.emulator.pointer.UnicornPointer;
import cn.banny.emulator.svc.Svc;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;
import unicorn.UnicornException;

import java.io.File;
import java.util.*;

/**
 * http://androidxref.com/4.4.4_r1/xref/external/kernel-headers/original/asm-arm/unistd.h
 */
public class LinuxSyscallHandler extends SyscallHandler {

    private static final Log log = LogFactory.getLog(LinuxSyscallHandler.class);

    private final SvcMemory svcMemory;

    public LinuxSyscallHandler(Dlfcn dlfcn, SvcMemory svcMemory) {
        super(dlfcn);

        this.svcMemory = svcMemory;
    }

    @Override
    public void hook(Unicorn u, int intno, Object user) {
        Emulator emulator = (Emulator) user;

        if (log.isDebugEnabled()) {
            ARM.showThumbRegs(u);
        }

        Pointer pc = UnicornPointer.register(u, ArmConst.UC_ARM_REG_PC);
        final int svcNumber;
        if (ARM.isThumb(u)) {
            svcNumber = pc.getShort(-2) & 0xff;
        } else {
            svcNumber = pc.getInt(-4) & 0xffffff;
        }

        int NR = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R7)).intValue();
        String syscall = null;
        Throwable exception = null;
        try {
            if (svcNumber != 0) {
                Svc svc = svcMemory.getSvc(svcNumber);
                if (svc != null) {
                    u.reg_write(ArmConst.UC_ARM_REG_R0, svc.handle(u, emulator));
                    return;
                }
                u.emu_stop();
                throw new IllegalStateException("svc number: " + svcNumber);
            }

            if (intno == 2) {
                switch (NR) {
                    case 2:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, fork(emulator));
                        return;
                    case 3:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, read(u, emulator));
                        return;
                    case 4:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, write(u, emulator));
                        return;
                    case 5:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, open(u, emulator));
                        return;
                    case 6:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, close(u, emulator));
                        return;
                    case 11:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, execve(u, emulator));
                        return;
                    case 19:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, lseek(u, emulator));
                        return;
                    case  20: // getpid
                    case 224: // gettid
                        u.reg_write(ArmConst.UC_ARM_REG_R0, emulator.getPid());
                        return;
                    case 33:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, access(u, emulator));
                        return;
                    case 36: // sync: causes all pending modifications to filesystem metadata and cached file data to be written to the underlying filesystems.
                        return;
                    case 37:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, kill(u));
                        return;
                    case 39:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, mkdir(u, emulator));
                        return;
                    case 41:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, dup(u, emulator));
                        return;
                    case 45:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, brk(u, emulator));
                        return;
                    case 54:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, ioctl(u, emulator));
                        return;
                    case 60:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, umask(u));
                        return;
                    case 63:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, dup2(u, emulator));
                        return;
                    case 67:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, sigaction(u));
                        return;
                    case 78:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, gettimeofday(u));
                        return;
                    case 88:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, reboot(u, emulator));
                        return;
                    case 91:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, munmap(u, emulator));
                        return;
                    case 93:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, ftruncate(u));
                        return;
                    case 94:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, fchmod(u));
                        return;
                    case 103:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, syslog(u));
                        return;
                    case 104:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, setitimer(u));
                        return;
                    case 120:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, clone(u, emulator));
                        return;
                    case 122:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, uname(u));
                        return;
                    case 125:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, mprotect(u, emulator));
                        return;
                    case 126:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, sigprocmask(u, emulator));
                        return;
                    case 132:
                        syscall = "getpgid";
                        break;
                    case 136:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, personality(u));
                        return;
                    case 140:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, llseek(u, emulator));
                        return;
                    case 142:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, newselect(u, emulator));
                        return;
                    case 143:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, flock(u));
                        return;
                    case 146:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, writev(u, emulator));
                        return;
                    case 162:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, nanosleep(u));
                        return;
                    case 168:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, poll(u));
                        return;
                    case 172:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, prctl(u));
                        return;
                    case 183:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, getcwd(u, emulator));
                        return;
                    case 192:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, mmap2(u, emulator));
                        return;
                    case 195:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, stat64(u, emulator));
                        return;
                    case 196:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, lstat(u, emulator));
                        return;
                    case 197:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, fstat(u, emulator));
                        return;
                    case 199: // getuid
                    case 200: // getgid
                    case 201: // geteuid
                    case 202: // getegid
                        u.reg_write(ArmConst.UC_ARM_REG_R0, 0);
                        return;
                    case 205:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, getgroups(u));
                        return;
                    case 208:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, setresuid32(u));
                        return;
                    case 210:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, setresgid32(u));
                        return;
                    case 220:
                        syscall = "madvise";
                        u.reg_write(ArmConst.UC_ARM_REG_R0, 0);
                        return;
                    case 221:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, fcntl(u, emulator));
                        return;
                    case 230:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, lgetxattr(u));
                        return;
                    case 240:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, futex(u));
                        return;
                    case 248:
                        exit_group(u);
                        return;
                    case 263:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, clock_gettime(u));
                        return;
                    case 266:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, statfs(u));
                        return;
                    case 268:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, tgkill(u));
                        return;
                    case 281:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, socket(u, emulator));
                        return;
                    case 283:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, connect(u, emulator));
                        return;
                    case 286:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, getsockname(u, emulator));
                        return;
                    case 287:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, getpeername(u, emulator));
                        return;
                    case 290:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, sendto(u, emulator));
                        return;
                    case 293:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, shutdown(u, emulator));
                        return;
                    case 294:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, setsockopt(u, emulator));
                        return;
                    case 295:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, getsockopt(u, emulator));
                        return;
                    case 322:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, openat(u, emulator));
                        return;
                    case 334:
                        u.reg_write(ArmConst.UC_ARM_REG_R0, faccessat(u, emulator));
                        return;
                }
            }
        } catch (UnsupportedOperationException e) {
            exception = e;
        } catch (Throwable e) {
            u.emu_stop();
            exception = e;
        }

        if (log.isDebugEnabled()) {
            log.warn("handleInterrupt intno=" + intno + ", NR=" + NR + ", svcNumber=0x" + Integer.toHexString(svcNumber) + ", PC=" + pc + ", syscall=" + syscall, exception);
        }

        if (exception instanceof UnicornException) {
            throw (UnicornException) exception;
        }
    }

    private int fork(Emulator emulator) {
        log.debug("fork");
        emulator.getMemory().setErrno(Emulator.ENOSYS);
        return -1;
    }

    private int tgkill(Unicorn u) {
        int tgid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int tid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int sig = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("tgkill tgid=" + tgid + ", tid=" + tid + ", sig=" + sig);
        }
        return 0;
    }

    private int clone(Unicorn u, Emulator emulator) {
        int flags = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer child_stack = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer pid = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        Pointer tls = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
        Pointer ctid = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R4);
        Pointer fn = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R5);
        Pointer arg = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R6);
        if (log.isDebugEnabled()) {
            log.debug("clone flags=0x" + Integer.toHexString(flags) + ", child_stack=" + child_stack + ", pid=" + pid + ", tls=" + tls + ", ctid=" + ctid + ", fn=" + fn + ", arg=" + arg);
        }
        emulator.getMemory().setErrno(Emulator.EAGAIN);
        throw new AbstractMethodError();
    }

    private int flock(Unicorn u) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int operation = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("flock fd=" + fd + ", operation=" + operation);
        }
        return 0;
    }

    private int fchmod(Unicorn u) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int mode = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("fchmod fd=" + fd + ", mode=" + mode);
        }
        return 0;
    }

    private int llseek(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        long offset_high = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue() & 0xffffffffL;
        long offset_low = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue() & 0xffffffffL;
        Pointer result = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
        int whence = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R4)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("llseek fd=" + fd + ", offset_high=" + offset_high + ", offset_low=" + offset_low + ", result=" + result + ", whence=" + whence);
        }

        FileIO io = fdMap.get(fd);
        if (io == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        } else {
            return io.llseek(offset_high, offset_low, result, whence);
        }
    }

    private int access(Unicorn u, Emulator emulator) {
        Pointer pathname = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        int mode = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("access pathname=" + pathname.getString(0) + ", mode=" + mode);
        }
        emulator.getMemory().setErrno(Emulator.EACCES);
        return -1;
    }

    private int execve(Unicorn u, Emulator emulator) {
        Pointer filename = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        Pointer argv = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer envp = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        assert filename != null;
        if (log.isDebugEnabled()) {
            List<String> args = new ArrayList<>();
            Pointer pointer;
            while ((pointer = argv.getPointer(0)) != null) {
                args.add(pointer.getString(0));
                argv = argv.share(4);
            }
            List<String> env = new ArrayList<>();
            while ((pointer = envp.getPointer(0)) != null) {
                env.add(pointer.getString(0));
                envp = envp.share(4);
            }
            log.debug("execve filename=" + filename.getString(0) + ", args=" + args + ", env=" + env);
        }
        emulator.getMemory().setErrno(Emulator.EACCES);
        return -1;
    }

    private long persona;

    private int personality(Unicorn u) {
        long persona = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("personality persona=0x" + Long.toHexString(persona));
        }
        int old = (int) this.persona;
        if (persona == 0xffffffffL) {
            return old;
        } else {
            this.persona = persona;
            return old;
        }
    }

    private int shutdown(Unicorn u, Emulator emulator) {
        int sockfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int how = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("shutdown sockfd=" + sockfd + ", how=" + how);
        }

        FileIO io = fdMap.get(sockfd);
        if (io == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return io.shutdown(how);
    }

    private int dup(Unicorn u, Emulator emulator) {
        int oldfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();

        FileIO io = fdMap.get(oldfd);
        if (io == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        if (log.isDebugEnabled()) {
            log.debug("dup oldfd=" + oldfd + ", io=" + io);
        }
        FileIO _new = io.dup2();
        if (_new == null) {
            throw new UnsupportedOperationException();
        }
        int newfd = getMinFd();
        fdMap.put(newfd, _new);
        return newfd;
    }

    private int stat64(Unicorn u, Emulator emulator) {
        Pointer pathname = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        Pointer statbuf = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        if (log.isDebugEnabled()) {
            log.debug("stat64 pathname=" + pathname.getString(0) + ", statbuf=" + statbuf);
        }
        return emulator.getMemory().stat64(pathname.getString(0), statbuf);
    }

    private int lstat(Unicorn u, Emulator emulator) {
        Pointer pathname = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        Pointer statbuf = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        if (log.isDebugEnabled()) {
            log.debug("lstat pathname=" + pathname.getString(0) + ", statbuf=" + statbuf);
        }
        return emulator.getMemory().stat64(pathname.getString(0), statbuf);
    }

    private int newselect(Unicorn u, Emulator emulator) {
        int nfds = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer readfds = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer writefds = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        Pointer exceptfds = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
        Pointer timeout = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R4);
        int size = (nfds - 1) / 8 + 1;
        if (log.isDebugEnabled()) {
            log.debug("newselect nfds=" + nfds + ", readfds=" + readfds + ", writefds=" + writefds + ", exceptfds=" + exceptfds + ", timeout=" + timeout);
            if (readfds != null) {
                byte[] data = readfds.getByteArray(0, size);
                Inspector.inspect(data, "readfds");
            }
            if (writefds != null) {
                byte[] data = writefds.getByteArray(0, size);
                Inspector.inspect(data, "writefds");
            }
        }
        if (exceptfds != null) {
            emulator.getMemory().setErrno(Emulator.ENOMEM);
            return -1;
        }
        if (writefds != null) {
            int count = select(nfds, writefds, readfds);
            if (count > 0) {
                return count;
            }
        }
        if (readfds != null) {
            int count = select(nfds, readfds, writefds);
            if (count > 0) {
                return count;
            }
        }
        throw new AbstractMethodError();
    }

    private int select(int nfds, Pointer checkfds, Pointer clearfds) {
        int count = 0;
        for (int i = 0; i < nfds; i++) {
            int mask = checkfds.getInt(i / 32);
            if(((mask >> i) & 1) == 1) {
                count++;
            }
        }
        if (count > 0) {
            if (clearfds != null) {
                for (int i = 0; i < nfds; i++) {
                    clearfds.setInt(i / 32, 0);
                }
            }
        }
        return count;
    }

    private int getpeername(Unicorn u, Emulator emulator) {
        int sockfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer addr = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer addrlen = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        if (log.isDebugEnabled()) {
            log.debug("getpeername sockfd=" + sockfd + ", addr=" + addr + ", addrlen=" + addrlen);
        }

        FileIO io = fdMap.get(sockfd);
        if (io == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }

        return io.getpeername(addr, addrlen);
    }

    private int poll(Unicorn u) {
        Pointer fds = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        int nfds = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int timeout = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        int count = 0;
        for (int i = 0; i < nfds; i++) {
            Pointer pollfd = fds.share(i * 8);
            int fd = pollfd.getInt(0);
            short events = pollfd.getShort(4);
            if (log.isDebugEnabled()) {
                log.debug("poll fds=" + fds + ", nfds=" + nfds + ", timeout=" + timeout + ", fd=" + fd + ", events=" + events);
            }
            if (fd < 0) {
                pollfd.setShort(6, (short) 0);
            } else {
                pollfd.setShort(6, events);
                count++;
            }
        }
        return count;
    }

    private int mask = 0x12;

    private int umask(Unicorn u) {
        int mask = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("umask mask=0x" + Long.toHexString(mask));
        }
        int old = this.mask;
        this.mask = mask;
        return old;
    }

    private int setresuid32(Unicorn u) {
        int ruid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int euid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int suid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("setresuid32 ruid=" + ruid + ", euid=" + euid + ", suid=" + suid);
        }
        return 0;
    }

    private int setresgid32(Unicorn u) {
        int rgid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int egid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int sgid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("setresgid32 rgid=" + rgid + ", egid=" + egid + ", sgid=" + sgid);
        }
        return 0;
    }

    private int mkdir(Unicorn u, Emulator emulator) {
        Pointer pathname = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        int mode = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("mkdir pathname=" + pathname.getString(0) + ", mode=" + mode);
        }
        emulator.getMemory().setErrno(Emulator.EACCES);
        return -1;
    }

    private int syslog(Unicorn u) {
        int type = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer bufp = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int len = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("syslog type=" + type + ", bufp=" + bufp + ", len=" + len);
        }
        throw new UnsupportedOperationException();
    }

    private int sigprocmask(Unicorn u, Emulator emulator) {
        int how = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer set = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer oldset = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        if (log.isDebugEnabled()) {
            log.debug("sigprocmask how=" + how + ", set=" + set + ", oldset=" + oldset);
        }
        emulator.getMemory().setErrno(Emulator.EINVAL);
        return -1;
    }

    private int lgetxattr(Unicorn u) {
        Pointer path = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        Pointer name = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer value = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        int size = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R3)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("lgetxattr path=" + path.getString(0) + ", name=" + name.getString(0) + ", value=" + value + ", size=" + size);
        }
        throw new UnsupportedOperationException();
    }

    private int reboot(Unicorn u, Emulator emulator) {
        int magic = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int magic2 = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int cmd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        Pointer arg = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
        if (log.isDebugEnabled()) {
            log.debug("reboot magic=" + magic + ", magic2=" + magic2 + ", cmd=" + cmd + ", arg=" + arg);
        }
        emulator.getMemory().setErrno(Emulator.EPERM);
        return -1;
    }

    private int nanosleep(Unicorn u) {
        Pointer req = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        Pointer rem = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int tv_sec = req.getInt(0);
        int tv_nsec = req.getInt(4);
        if (log.isDebugEnabled()) {
            log.debug("nanosleep req=" + req + ", rem=" + rem + ", tv_sec=" + tv_sec + ", tv_nsec=" + tv_nsec);
        }
        try {
            Thread.sleep(tv_sec * 1000L + tv_nsec / 1000000L);
        } catch (InterruptedException ignored) {
        }
        return 0;
    }

    private int kill(Unicorn u) {
        int pid = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int sig = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("kill pid=" + pid + ", sig=" + sig);
        }
        throw new UnsupportedOperationException();
    }

    private int setitimer(Unicorn u) {
        int which = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer new_value = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer old_value = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        if (log.isDebugEnabled()) {
            log.debug("setitimer which=" + which + ", new_value=" + new_value + ", old_value=" + old_value);
        }
        return 0;
    }

    private final Map<Integer, byte[]> sigMap = new HashMap<>();

    private static final int SIGHUP = 1;
    private static final int SIGINT = 2;
    private static final int SIGQUIT = 3;
    private static final int SIGABRT = 6;
    private static final int SIGPIPE = 13;
    private static final int SIGALRM = 14;
    private static final int SIGTERM = 15;
    private static final int SIGCHLD = 17;
    private static final int SIGTSTP = 20;
    private static final int SIGTTIN = 21;
    private static final int SIGTTOU = 22;
    private static final int SIGWINCH = 28;

    private int sigaction(Unicorn u) {
        int signum = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer act = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer oldact = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);

        String prefix = "Unknown";
        if (signum > 32) {
            signum -= 32;
            prefix = "Real-time";
        }
        if (log.isDebugEnabled()) {
            log.debug("sigaction signum=" + signum + ", act=" + act + ", oldact=" + oldact + ", prefix=" + prefix);
        }

        final int ACT_SIZE = 16;
        if (oldact != null) {
            byte[] lastAct = sigMap.get(signum);
            byte[] data = lastAct == null ? new byte[ACT_SIZE] : lastAct;
            oldact.write(0, data, 0, data.length);
        }

        switch (signum) {
            case SIGHUP:
            case SIGINT:
            case SIGQUIT:
            case SIGABRT:
            case SIGPIPE:
            case SIGALRM:
            case SIGTERM:
            case SIGCHLD:
            case SIGTSTP:
            case SIGTTIN:
            case SIGTTOU:
            case SIGWINCH:
                if (act != null) {
                    sigMap.put(signum, act.getByteArray(0, ACT_SIZE));
                }
                return 0;
        }

        throw new UnsupportedOperationException();
    }

    private int sendto(Unicorn u, Emulator emulator) {
        int sockfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer buf = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int len = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        int flags = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R3)).intValue();
        Pointer dest_addr = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R4);
        int addrlen = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R5)).intValue();

        byte[] data = buf.getByteArray(0, len);
        if (log.isDebugEnabled()) {
            Inspector.inspect(data, "sendto sockfd=" + sockfd + ", buf=" + buf + ", flags=" + flags + ", dest_addr=" + dest_addr + ", addrlen=" + addrlen);
        }
        FileIO file = fdMap.get(sockfd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.sendto(data, flags, dest_addr, addrlen);
    }

    private int connect(Unicorn u, Emulator emulator) {
        int sockfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer addr = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int addrlen = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            byte[] data = addr.getByteArray(0, addrlen);
            Inspector.inspect(data, "connect sockfd=" + sockfd + ", addr=" + addr + ", addrlen=" + addrlen);
        }

        FileIO file = fdMap.get(sockfd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.connect(addr, addrlen);
    }

    private int getsockname(Unicorn u, Emulator emulator) {
        int sockfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer addr = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        Pointer addrlen = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
        if (log.isDebugEnabled()) {
            log.debug("getsockname sockfd=" + sockfd + ", addr=" + addr + ", addrlen=" + addrlen);
        }
        FileIO file = fdMap.get(sockfd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.getsockname(addr, addrlen);
    }

    private int getsockopt(Unicorn u, Emulator emulator) {
        int sockfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int level = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int optname = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        Pointer optval = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
        Pointer optlen = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R4);
        if (log.isDebugEnabled()) {
            log.debug("getsockopt sockfd=" + sockfd + ", level=" + level + ", optname=" + optname + ", optval=" + optval + ", optlen=" + optlen);
        }

        FileIO file = fdMap.get(sockfd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.getsockopt(level, optname, optval, optlen);
    }

    private int setsockopt(Unicorn u, Emulator emulator) {
        int sockfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int level = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int optname = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        Pointer optval = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
        int optlen = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R4)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("setsockopt sockfd=" + sockfd + ", level=" + level + ", optname=" + optname + ", optval=" + optval + ", optlen=" + optlen);
        }

        FileIO file = fdMap.get(sockfd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.setsockopt(level, optname, optval, optlen);
    }

    private int socket(Unicorn u, Emulator emulator) {
        int domain = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int type = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int protocol = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("socket domain=" + domain + ", type=" + type + ", protocol=" + protocol);
        }

        if (protocol == SocketIO.IPPROTO_ICMP) {
            throw new UnsupportedOperationException();
        }

        int fd;
        switch (domain) {
            case SocketIO.AF_UNSPEC:
                throw new UnsupportedOperationException();
            case SocketIO.AF_LOCAL:
                if (type != SocketIO.SOCK_STREAM) {
                    throw new AbstractMethodError();
                }
                fd = getMinFd();
                fdMap.put(fd, new LocalSocketIO(emulator));
                return fd;
            case SocketIO.AF_INET:
                switch (type) {
                    case SocketIO.SOCK_STREAM:
                        fd = getMinFd();
                        fdMap.put(fd, new StreamSocket(emulator));
                        return fd;
                    case SocketIO.SOCK_DGRAM:
                        fd = getMinFd();
                        fdMap.put(fd, new UdpSocket());
                        return fd;
                    case SocketIO.SOCK_RAW:
                        throw new UnsupportedOperationException();
                }
                break;
        }
        throw new UnsupportedOperationException();
    }

    private int getgroups(Unicorn u) {
        int size = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer list = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        if (log.isDebugEnabled()) {
            log.debug("getgroups size=" + size + ", list=" + list);
        }
        return 0;
    }

    private int uname(Unicorn u) {
        Pointer buf = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        if (log.isDebugEnabled()) {
            log.debug("uname buf=" + buf);
        }

        final int SYS_NMLN = 65;

        Pointer sysname = buf.share(0);
        sysname.setString(0, "Linux");

        Pointer nodename = sysname.share(SYS_NMLN);
        nodename.setString(0, "localhost");

        Pointer release = nodename.share(SYS_NMLN);
        release.setString(0, "3.4.0-cyanogenmod+");

        Pointer version = release.share(SYS_NMLN);
        version.setString(0, "#1 SMP PREEMPT Thu Apr 19 14:36:58 CST 2018");

        Pointer machine = version.share(SYS_NMLN);
        machine.setString(0, "armv7l");

        Pointer domainname = machine.share(SYS_NMLN);
        domainname.setString(0, "");

        return 0;
    }

    private int getcwd(Unicorn u, Emulator emulator) {
        UnicornPointer buf = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        int size = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        File workDir = emulator.getWorkDir();
        String path = workDir == null ? "/" : workDir.getAbsolutePath();
        if (log.isDebugEnabled()) {
            log.debug("getcwd buf=" + buf + ", size=" + size + ", path=" + path);
        }
        buf.setString(0, path);
        return (int) buf.peer;
    }

    private void exit_group(Unicorn u) {
        int status = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("exit with code: " + status);
        }
        u.emu_stop();
    }

    private int munmap(Unicorn u, Emulator emulator) {
        long start = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
        int length = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("munmap start=0x" + Long.toHexString(start) + ", length=" + length);
        }
        return emulator.getMemory().munmap(start, length);
    }

    private int statfs(Unicorn u) {
        Pointer pathPointer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        Pointer buf = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        String path = pathPointer.getString(0);
        if (log.isDebugEnabled()) {
            log.debug("statfs pathPointer=" + pathPointer + ", buf=" + buf + ", path=" + path);
        }
        if("/sys/fs/selinux".equals(path)) {
            return -1;
        }
        throw new UnsupportedOperationException();
    }

    private static final int BIONIC_PR_SET_VMA =              0x53564d41;

    private int prctl(Unicorn u) {
        int option = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        long arg2 = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("prctl option=0x" + Integer.toHexString(option) + ", arg2=0x" + Long.toHexString(arg2));
        }
        switch (option) {
            case BIONIC_PR_SET_VMA:
                Pointer addr = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R2);
                int len = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R3)).intValue();
                Pointer pointer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R4);
                if (log.isDebugEnabled()) {
                    log.debug("prctl addr=" + addr + ", len=" + len + ", pointer=" + pointer + ", name=" + pointer.getString(0));
                }
                return 0;
        }
        throw new UnsupportedOperationException();
    }

    private static final int CLOCK_MONOTONIC = 1;
    private static final int CLOCK_BOOTTIME = 7;

    private long nanoTime = System.nanoTime();

    private int clock_gettime(Unicorn u) {
        int clk_id = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer tp = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        long offset = System.nanoTime() - nanoTime;
        long tv_sec = offset / 1000000000L;
        long tv_nsec = offset % 1000000000L;
        if (log.isDebugEnabled()) {
            log.debug("clock_gettime clk_id=" + clk_id + ", tp=" + tp + ", offset=" + offset + ", tv_sec=" + tv_sec + ", tv_nsec=" + tv_nsec);
        }
        switch (clk_id) {
            case CLOCK_MONOTONIC:
            case CLOCK_BOOTTIME:
                tp.setInt(0, (int) tv_sec);
                tp.setInt(4, (int) tv_nsec);
                return 0;
        }
        throw new UnsupportedOperationException();
    }

    private int fcntl(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int cmd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int arg = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("fcntl fd=" + fd + ", cmd=" + cmd + ", arg=" + arg);
        }

        FileIO file = fdMap.get(fd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.fcntl(cmd, arg);
    }

    private int writev(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer iov = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int iovcnt = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            for (int i = 0; i < iovcnt; i++) {
                Pointer iov_base = iov.getPointer(i * 8);
                int iov_len = iov.getInt(i * 8 + 4);
                byte[] data = iov_base.getByteArray(0, iov_len);
                Inspector.inspect(data, "writev fd=" + fd + ", iov=" + iov + ", iov_base=" + iov_base);
            }
        }

        FileIO file = fdMap.get(fd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }

        int count = 0;
        for (int i = 0; i < iovcnt; i++) {
            Pointer iov_base = iov.getPointer(i * 8);
            int iov_len = iov.getInt(i * 8 + 4);
            byte[] data = iov_base.getByteArray(0, iov_len);
            count += file.write(data);
        }
        return count;
    }

    private static final int FUTEX_WAIT = 0;
    private static final int FUTEX_WAKE = 1;

    private int futex(Unicorn u) {
        Pointer uaddr = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        int futex_op = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int val = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        int old = uaddr.getInt(0);
        if (log.isDebugEnabled()) {
            log.debug("futex uaddr=" + uaddr + ", _futexop=" + futex_op + ", op=" + (futex_op & 0x7f) + ", val=" + val + ", old=" + old);
        }

        switch (futex_op & 0x7f) {
            case FUTEX_WAIT:
                if (old != val) {
                    throw new IllegalStateException("old=" + old + ", val=" + val);
                }
                Pointer timeout = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R3);
                int mytype = val & 0xc000;
                int shared = val & 0x2000;
                if (log.isDebugEnabled()) {
                    log.debug("futex FUTEX_WAIT mytype=" + mytype + ", shared=" + shared + ", timeout=" + timeout + ", test=" + (mytype | shared));
                }
                uaddr.setInt(0, mytype | shared);
                return 0;
            case FUTEX_WAKE:
                return 0;
            default:
                throw new AbstractMethodError();
        }
    }

    private int brk(Unicorn u, Emulator emulator) {
        long address = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("brk address=0x" + Long.toHexString(address));
        }
        return emulator.getMemory().brk(address);
    }

    private int mprotect(Unicorn u, Emulator emulator) {
        long address = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
        int length = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int prot = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        long aligned = ARM.alignSize(length, emulator.getPageAlign());
        if (log.isDebugEnabled()) {
            log.debug("mprotect address=0x" + Long.toHexString(address) + ", length=" + length + ", aligned=" + aligned + ", prot=0x" + Integer.toHexString(prot));
        }
        return emulator.getMemory().mprotect(address, (int) aligned, prot);
    }

    private static final int MMAP2_SHIFT = 12;

    private int mmap2(Unicorn u, Emulator emulator) {
        long start = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue() & 0xffffffffL;
        int length = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int prot = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        int flags = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R3)).intValue();
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R4)).intValue();
        int offset = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R5)).intValue() << MMAP2_SHIFT;
        if (log.isDebugEnabled()) {
            log.debug("mmap2 start=0x" + Long.toHexString(start) + ", length=" + length + ", prot=0x" + Integer.toHexString(prot) + ", flags=0x" + Integer.toHexString(flags) + ", fd=" + fd + ", offset=" + offset);
        }
        return emulator.getMemory().mmap2(start, length, prot, flags, fd, offset);
    }

    private int gettimeofday(Unicorn u) {
        Pointer tv = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        Pointer tz = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        if (log.isDebugEnabled()) {
            log.debug("gettimeofday tv=" + tv + ", tz=" + tz);
        }

        if (log.isDebugEnabled()) {
            byte[] before = tv.getByteArray(0, 8);
            Inspector.inspect(before, "gettimeofday tv");
        }
        if (tz != null && log.isDebugEnabled()) {
            byte[] before = tz.getByteArray(0, 8);
            Inspector.inspect(before, "gettimeofday tz");
        }

        long currentTimeMillis = System.currentTimeMillis();
        long tv_sec = currentTimeMillis / 1000;
        long tv_usec = (currentTimeMillis % 1000) * 1000;
        tv.setInt(0, (int) tv_sec);
        tv.setInt(4, (int) tv_usec);

        if (tz != null) {
            Calendar calendar = Calendar.getInstance();
            int tz_minuteswest = -(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / (60 * 1000);
            tz.setInt(0, tz_minuteswest);
            tz.setInt(4, 0); // tz_dsttime
        }

        if (log.isDebugEnabled()) {
            byte[] after = tv.getByteArray(0, 8);
            Inspector.inspect(after, "gettimeofday tv after tv_sec=" + tv_sec + ", tv_usec=" + tv_usec);
        }
        if (tz != null && log.isDebugEnabled()) {
            byte[] after = tz.getByteArray(0, 8);
            Inspector.inspect(after, "gettimeofday tz after");
        }
        return 0;
    }

    private int faccessat(Unicorn u, Emulator emulator) {
        int dirfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer pathname_p = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int oflags = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        int mode = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R3)).intValue();
        String pathname = pathname_p.getString(0);
        if (log.isDebugEnabled()) {
            log.debug("faccessat dirfd=" + dirfd + ", pathname=" + pathname + ", oflags=0x" + Integer.toHexString(oflags) + ", mode=" + Integer.toHexString(mode));
        }
        emulator.getMemory().setErrno(Emulator.EACCES);
        return -1;
    }

    private int openat(Unicorn u, Emulator emulator) {
        int dirfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer pathname_p = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int oflags = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        int mode = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R3)).intValue();
        String pathname = pathname_p.getString(0);
        int fd = emulator.getMemory().open(pathname, oflags);
        if (log.isDebugEnabled()) {
            log.debug("openat dirfd=" + dirfd + ", pathname=" + pathname + ", oflags=0x" + Integer.toHexString(oflags) + ", mode=" + Integer.toHexString(mode) + ", fd=" + fd);
        }
        return fd;
    }

    private int open(Unicorn u, Emulator emulator) {
        Pointer pathname_p = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R0);
        int oflags = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int mode = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        String pathname = pathname_p.getString(0);
        int fd = emulator.getMemory().open(pathname, oflags);
        if (log.isDebugEnabled()) {
            log.debug("open pathname=" + pathname + ", oflags=0x" + Integer.toHexString(oflags) + ", mode=" + Integer.toHexString(mode) + ", fd=" + fd);
        }
        return fd;
    }

    private int ftruncate(Unicorn u) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int length = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("ftruncate fd=" + fd + ", length=" + length);
        }
        FileIO file = fdMap.get(fd);
        if (file == null) {
            throw new UnsupportedOperationException();
        }
        return file.ftruncate(length);
    }

    private int lseek(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int offset = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        int whence = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("lseek fd=" + fd + ", offset=" + offset + ", whence=" + whence);
        }
        FileIO file = fdMap.get(fd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.lseek(offset, whence);
    }

    private int close(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("close fd=" + fd);
        }

        FileIO file = fdMap.remove(fd);
        if (file != null) {
            file.close();
            return 0;
        } else {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
    }

    private int fstat(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer stat = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        if (log.isDebugEnabled()) {
            log.debug("fstat fd=" + fd + ", stat=" + stat);
        }

        FileIO file = fdMap.get(fd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        if (log.isDebugEnabled()) {
            log.debug("fstat file=" + file + ", stat=" + stat);
        }
        return file.fstat(emulator, u, stat);
    }

    private int ioctl(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        long request = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue() & 0xffffffffL;
        long argp = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue() & 0xffffffffL;
        if (log.isDebugEnabled()) {
            log.debug("ioctl fd=" + fd + ", request=0x" + Long.toHexString(request) + ", argp=0x" + Long.toHexString(argp));
        }

        FileIO file = fdMap.get(fd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.ioctl(u, request, argp);
    }

    private int write(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer buffer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int count = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        byte[] data = buffer.getByteArray(0, count);
        if (log.isDebugEnabled()) {
            Inspector.inspect(data, "write fd=" + fd);
        }

        FileIO file = fdMap.get(fd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.write(data);
    }

    private int read(Unicorn u, Emulator emulator) {
        int fd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        Pointer buffer = UnicornPointer.register(u, ArmConst.UC_ARM_REG_R1);
        int count = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R2)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("read fd=" + fd + ", buffer=" + buffer + ", count=" + count);
        }

        FileIO file = fdMap.get(fd);
        if (file == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }
        return file.read(u, buffer, count);
    }

    private int dup2(Unicorn u, Emulator emulator) {
        int oldfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R0)).intValue();
        int newfd = ((Number) u.reg_read(ArmConst.UC_ARM_REG_R1)).intValue();
        if (log.isDebugEnabled()) {
            log.debug("dup2 oldfd=" + oldfd + ", newfd=" + newfd);
        }

        FileIO old = fdMap.get(oldfd);
        if (old == null) {
            emulator.getMemory().setErrno(Emulator.EBADF);
            return -1;
        }

        if (oldfd == newfd) {
            return newfd;
        }
        FileIO _new = fdMap.remove(newfd);
        if (_new != null) {
            _new.close();
        }
        _new = old.dup2();
        fdMap.put(newfd, _new);
        return newfd;
    }

}
