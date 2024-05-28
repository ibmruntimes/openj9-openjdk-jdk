/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2024, 2024 All Rights Reserved
 * ===========================================================================
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * IBM designates this particular file as subject to the "Classpath" exception
 * as provided by IBM in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, see <http://www.gnu.org/licenses/>.
 *
 * ===========================================================================
 */

package jdk.internal.foreign;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;

/* Inlined libc function symbols missing in the default library on z/OS. */
public enum ZosFuncSymbols {
    abort, abs, accept, accept_and_recv, access, acl_create_entry, acl_delete_entry, acl_delete_fd,
    acl_delete_file, acl_first_entry, acl_free, acl_from_text, acl_get_entry, acl_get_fd, acl_get_file,
    acl_init, acl_set_fd, acl_set_file, acl_sort, acl_to_text, acl_update_entry, acl_valid, acos,
    acosf, acosl, acosh, acoshf, acoshl, __ae_correstbl_query, alarm, asctime, asctime64, asctime64_r,
    asin, asinf, asinl, asinh, asinhf, asinhl, atan, atanf, atanl, atan2, atan2f, atan2l, atanh,
    atanhf, atanhl, atexit, __atoe, __atoe_l, atof, atoi, atol, atoll, __a2e_l, __a2e_s, a64l,
    basename, bcmp, bcopy, bind, bsd_signal, bsearch, btowc, bzero, __cabend, cabs, cabsf, cabsl,
    cacos, cacosf, cacosl, cacosh, cacoshf, cacoshl, calloc, carg, cargf, cargl, casin, casinf,
    casinl, casinh, casinhf, casinhl, catan, catanf, catanl, catanh, catanhf, catanhl, catclose,
    catgets, catopen, cbrt, cbrtf, cbrtl, cclass, ccos, ccosf, ccosl, ccosh, ccoshf, ccoshl,
    __CcsidType, cdump, ceil, ceilf, ceill, __certificate, cexp, cexpf, cexpl, cfgetispeed, cfgetospeed,
    cfsetispeed, cfsetospeed, __chattr, __chattr64, chaudit, chdir, __check_resource_auth_np, CheckSchEnv,
    chmod, chown, chroot, cimag, cimagf, cimagl, clearenv, clearerr, clock, clog, clogf, clogl,
    close, closedir, closelog, clrmemf, __cnvblk, collequiv, collorder, collrange, colltostr, confstr,
    conj, conjf, conjl, connect, ConnectExportImport, ConnectServer, ConnectWorkMgr, __console, __console2,
    ContinueWorkUnit, __convert_id_np, copysign, copysignf, copysignl, cos, cosf, cosl, cosh, coshf,
    coshl, __cotan, __cotanf, __cotanl, __cpl, cpow, cpowf, cpowl, cproj, cprojf, cprojl, creal, crealf,
    creall, creat, CreateWorkUnit, crypt, csid, csin, csinf, csinl, csinh, csinhf, csinhl, __CSNameType,
    csnap, csqrt, csqrtf, csqrtl, ctanh, ctanhf, ctanhl, ctermid, ctime, ctime64, ctime64_r, ctrace,
    cuserid, dbm_clearerr, dbm_close, dbm_delete, dbm_error, dbm_fetch, dbm_firstkey, dbm_nextkey, dbm_open,
    dbm_store, DeleteWorkUnit, difftime, difftime64, dirname, __discarddata, DisconnectServer, div, dllfree,
    dllload, dllqueryfn, dllqueryvar, dn_comp, dn_expand, dn_find, dn_skipname, drand48, dup, dup2, dynalloc,
    dynfree, ecvt, encrypt, endgrent, endhostent, endnetent, endprotoent, endpwent, endservent, endutxent,
    erand48, erf, erfc, erff, erfl, erfcf, erfcl, __etoa, __etoa_l, exit, _exit, _Exit, exp, expf,
    expl, expm1, expm1f, expm1l, ExportWorkUnit, exp2, exp2f, exp2l, extlink_np, ExtractWorkUnit, __e2a_l,
    __e2a_s, fabs, fabsf, fabsl, fattach, __fbufsize, __fchattr, __fchattr64, fchaudit, fchdir, fchmod,
    fchown, fclose, fcntl, fcvt, fdelrec, fdetach, fdim, fdimf, fdiml, fdopen, feclearexcept, fegetenv,
    fegetexceptflag, fegetround, feholdexcept, feof, feraiseexcept, ferror, fesetenv, fesetexceptflag,
    fesetround, fetch, fetchep, fetestexcept, feupdateenv, fflush, ffs, fgetc, fgetpos, fgets, fgetwc,
    fgetws, fileno, finite, __flbf, fldata, flocate, floor, floorf, floorl, _flushlbf, fma, fmaf, fmal,
    fmax, fmaxf, fmaxl, fmin, fminf, fminl, fmod, fmodf, fmodl, fmtmsg, fnmatch, fopen, fork,
    fp_clr_flag, fp_raise_xcp, fp_read_flag, fp_read_rnd, fp_swap_rnd, fpathconf, __fpending, fprintf,
    __fpurge, fputc, fputs, fputwc, fputws, fread, __freadable, __freadahead, __freading, free, freeaddrinfo,
    freopen, frexp, frexpf, frexpl, fscanf, fseek, fseeko, __fseterr, __fsetlocking, fsetpos, fstat,
    fstat64, fstatvfs, fsync, ftell, ftello, ftime, ftime64, ftok, ftruncate, ftrylockfile, ftw, ftw64,
    funlockfile, fupdate, fwide, fwprintf, __fwritable, fwrite, __fwriting, fwscanf, gai_strerror, gamma,
    gcvt, getaddrinfo, getc, getchar, getc_unlocked, getchar_unlocked, putc_unlocked, putchar_unlocked,
    getclientid, __getclientid, getcontext, getcwd, getdate, getdate64, getdtablesize, getegid, getenv,
    __getenv, geteuid, getgid, getgrent, getgrgid, getgrnam, getgroups, getgroupsbyname, gethostbyaddr,
    gethostbyname, gethostent, gethostid, gethostname, getibmopt, getibmsockopt, getitimer, getlogin,
    __getlogin1, getmccoll, getmsg, getnameinfo, getnetbyaddr, getnetbyname, getnetent, getopt, getpagesize,
    getpass, getpeername, getpgid, getpgrp, getpid, getpmsg, getppid, getpriority, getprotobyname,
    getprotobynumber, getprotoent, getpwent, getpwnam, getpwuid, getrlimit, getrusage, gets, getservbyname,
    getservbyport, getservent, getsid, getsockname, getsockopt, getstablesize, getsubopt, getsyntx, gettimeofday,
    gettimeofday64, getuid, getutxent, getutxent64, getutxid, getutxid64, getutxline, getutxline64, getw,
    getwc, getwchar, getwd, getwmccoll, givesocket, glob, globfree, gmtime, gmtime64, gmtime64_r, grantpt,
    hcreate, hdestroy, __heaprpt, hsearch, hypot, hypotf, hypotl, ibmsflush, iconv, iconv_close, iconv_open,
    if_freenameindex, if_indextoname, if_nameindex, if_nametoindex, ilogb, ilogbf, ilogbl, imaxabs, imaxdiv,
    ImportWorkUnit, index, inet6_opt_append, inet6_opt_find, inet6_opt_finish, inet6_opt_get_val, inet6_opt_init,
    inet6_opt_next, inet6_opt_set_val, inet6_rth_add, inet6_rth_getaddr, inet6_rth_init, inet6_rth_reverse,
    inet6_rth_segments, inet6_rth_space, inet_addr, inet_lnaof, inet_makeaddr, inet_netof, inet_network,
    inet_ntoa, inet_ntop, inet_pton, initgroups, initstate, insque, ioctl, __ipdbcs, __ipDomainName, __ipdspx,
    __iphost, __ipmsgc, __ipnode, __iptcpn, isalnum, isalpha, isascii, isastream, isatty, __isBFP, isblank,
    iscntrl, isdigit, isgraph, islower, ismccollel, __isPosixOn, isprint, ispunct, isspace, isupper, iswalnum,
    iswblank, iswcntrl, iswctype, iswdigit, iswgraph, iswlower, iswprint, iswpunct, iswspace, iswupper,
    iswxdigit, isxdigit, JoinWorkUnit, jrand48, j0, j1, jn, kill, killpg, labs, __lchattr, __lchattr64,
    lchown, lcong48, ldexp, ldexpf, ldexpl, ldiv, LeaveWorkUnit, __le_ceegtjs, __le_cib_get, __le_condition_token_build,
    __le_debug_set_resume_mch, __le_msg_add_insert, __le_msg_get, __le_msg_get_and_write, __le_msg_write,
    __le_record_dump, __le_traceback, lfind, lgamma, lgammaf, lgammal, __librel, link, listen, llabs, lldiv,
    llround, llroundf, llroundl, localdtconv, localeconv, localtime, localtime64, localtime64_r, lockf, log,
    logf, logl, logb, logbf, logbl, __login, __login_applid, log1p, log1pf, log1pl, log10, log10f, log10l,
    log2, log2f, log2l, longjmp, _longjmp, lrand48, lrint, lrintf, lrintl, llrint, llrintf, llrintl, lround,
    lroundf, lroundl, lsearch, lseek, lstat, lstat64, l64a, makecontext, malloc, __malloc24, __malloc31,
    maxcoll, maxdesc, mblen, mbrlen, mbrtowc, mbsinit, mbsrtowcs, mbstowcs, mbtowc, m_create_layout,
    m_destroy_layout, memccpy, memchr, memcmp, memcpy, memmove, memset, m_getvalues_layout, mkdir, mkfifo,
    mknod, mkstemp, mktemp, mktime, mktime64, __mlockall, mmap, modf, modff, modfl, mount, __mount, mprotect,
    mrand48, m_setvalues_layout, msgctl, msgctl64, msgget, msgrcv, __msgrcv_timed, msgsnd, msgxrcv, msgxrcv64,
    msync, m_transform_layout, munmap, __must_stay_clean, m_wtransform_layout, nan, nanf, nanl, nearbyint,
    nearbyintf, nearbyintl, nextafter, nextafterf, nextafterl, nexttoward, nexttowardf, nexttowardl, nftw,
    nftw64, nice, nlist, nl_langinfo, nrand48, open, opendir, __opendir2, openlog, __open_stat, __open_stat64,
    __osname, __passwd, __passwd_applid, pathconf, pause, pclose, perror, __pid_affinity, pipe, __poe, poll,
    popen,pow, powf, powl, __pow_i, __pow_ii, printf, pthread_attr_destroy, pthread_attr_getdetachstate,
    pthread_attr_getstacksize, pthread_attr_getsynctype_np, pthread_attr_getweight_np, pthread_attr_init,
    pthread_attr_setdetachstate, pthread_attr_setstacksize, pthread_attr_setsynctype_np, pthread_attr_setweight_np,
    pthread_cancel, pthread_cleanup_pop, pthread_cleanup_push, pthread_cond_broadcast, pthread_cond_destroy,
    pthread_cond_init, pthread_cond_signal, pthread_cond_timedwait, pthread_cond_timedwait64, pthread_cond_wait,
    pthread_condattr_destroy, pthread_condattr_getkind_np, pthread_condattr_init, pthread_condattr_setkind_np,
    pthread_create, pthread_detach, pthread_equal, pthread_exit, pthread_getspecific, pthread_getspecific_d8_np,
    pthread_join, pthread_join_d4_np, pthread_key_create, pthread_kill, pthread_mutex_destroy, pthread_mutex_init,
    pthread_mutex_lock, pthread_mutex_trylock, pthread_mutex_unlock, pthread_mutexattr_destroy, pthread_mutexattr_getkind_np,
    pthread_mutexattr_getpshared, pthread_mutexattr_gettype, pthread_mutexattr_init, pthread_mutexattr_setkind_np,
    pthread_mutexattr_setpshared, pthread_mutexattr_settype, pthread_once, pthread_rwlock_destroy, pthread_rwlock_init,
    pthread_rwlock_rdlock, pthread_rwlock_tryrdlock, pthread_rwlock_trywrlock, pthread_rwlock_unlock, pthread_rwlock_wrlock,
    pthread_rwlockattr_destroy, pthread_rwlockattr_getpshared, pthread_rwlockattr_init, pthread_rwlockattr_setpshared,
    pthread_security_np, pthread_security_applid_np, pthread_self, pthread_setintr, pthread_setintrtype,
    pthread_set_limit_np, pthread_setspecific, pthread_tag_np, pthread_testintr, pthread_yield, ptsname, putc,
    putchar, putenv, putmsg, putpmsg, puts, pututxline, pututxline64, putw, putwc, putwchar, qsort,
    QueryMetrics, QuerySchEnv, QueryWorkUnitClassification, raise, rand, random, read, readdir, __readdir2,
    __readdir2_64, readlink, readv, realloc, realpath, recv, recvfrom, recvmsg, regcomp, regerror, regexec,
    regfree, release, remainder, remainderf, remainderl, remove, remque, remquo, remquof, remquol, rename,
    res_init, res_mkquery, res_query, res_querydomain, res_search, res_send, __reset_exception_handler, rewind,
    rewinddir, rexec, rexec_af, rindex, rint, rintf, rintl, rmdir, round, roundf, roundl, rpmatch, scalb,
    scalbn, scalbnf, scalbnl, scalbln, scalblnf, scalblnl, scanf, sched_yield, seed48, seekdir, select,
    selectex, semctl, semctl64, semget, semop, __semop_timed, send, send_file, sendmsg, sendto, __server_classify,
    __server_classify_create, __server_classify_destroy, __server_classify_reset, __server_init, __server_pwu,
    __server_threads_query, setbuf, setcontext, setegid, setenv, seteuid, __set_exception_handler, setgid, setgrent,
    setgroups, sethostent, setibmopt, setibmsockopt, setipv4sourcefilter, setitimer, setjmp, _setjmp, setkey,
    setlocale, setlogmask, setnetent, setpgid, setpgrp, setpriority, setprotoent, setpwent, setregid, setreuid,
    setrlimit, setservent, setsid, setsockopt, setsourcefilter, setstate, setuid, setutxent, setvbuf, shmat,
    shmctl, shmctl64, shmdt, shmget, shutdown, __shutdown_registration, sigaction, __sigactionset, sigaddset,
    sigaltstack, sigdelset, sigemptyset, sigfillset, sighold, sigignore, siginterrupt, sigismember, siglongjmp,
    signal, __signgam, sigpause, sigpending, sigprocmask, sigrelse, sigset, sigsetjmp, sigstack, sigsuspend,
    sigwait, sin, sinf, sinl, sinh, sinhf, sinhl, sleep, __smf_record, __smf_record2, snprintf, sock_debug,
    sock_do_teststor, socket, socketpair, spawn, spawnp, __spawn2, __spawnp2, sprintf, sqrt, sqrtf, sqrtl,
    srand, srandom, srand48, sscanf, stat, stat64, statvfs, strcasecmp, strcat, strchr, strcmp, strcoll,
    strcpy, strcspn, strdup, strerror, strerror_r, strfmon, strftime, strlen, strncasecmp, strncat, strncmp,
    strncpy, strpbrk, strptime, strrchr, strspn, strstr, strtocoll, strtod, strtof, strtoimax, strtok,
    strtol, strtold, strtoll, strtoul, strtoull, strtoumax, strxfrm, __superkill, svc99, swab, swapcontext,
    swprintf, swscanf, symlink, sync, sysconf, syslog, system, t_accept, takesocket, t_alloc, tan, tanf,
    tanl, tanh, tanhf, tanhl, t_bind, tcdrain, tcflow, tcflush, tcgetattr, __tcgetcp, tcgetpgrp, tcgetsid,
    t_close, t_connect, tcperror, tcsendbreak, tcsetattr, __tcsetcp, tcsetpgrp, __tcsettables, tdelete,
    telldir, tempnam, t_error, tfind, t_free, tgamma, tgammaf, tgammal, t_getinfo, t_getprotaddr, t_getstate,
    time, time64, times, t_listen, t_look, tmpfile, tmpnam, toascii, __toCcsid, __toCSName, tolower, toupper,
    t_open, t_optmgmt, towlower, towupper, towctrans, t_rcv, t_rcvconnect, t_rcvdis, t_rcvrel, t_rcvudata,
    t_rcvuderr, trunc, truncf, truncl, truncate, tsearch, t_snd, t_snddis, t_sndrel, t_sndudata, t_strerror,
    t_sync, ttyname, ttyslot, t_unbind, twalk, tzset, ualarm, __ucreate, __ufree, __uheapreport, ulimit,
    __umalloc, umask, umount, uname, UnDoExportWorkUnit, UnDoImportWorkUnit, ungetc, ungetwc, unlink, unlockpt,
    unsetenv, usleep, utime, utime64, utimes, utimes64, __utmpxname, vfork, vfprintf, vfscanf, vscanf,
    vsscanf, vfwprintf, vfwscanf, vprintf, vsnprintf, vsprintf, vswprintf, vwprintf, vwscanf, vswscanf, wait,
    waitid, waitpid, wait3, wcrtomb, wcscat, wcschr, wcscmp, wcscoll, wcscpy, wcscspn, wcsftime, wcsid,
    wcslen, wcsncat, wcsncmp, wcsncpy, wcspbrk, wcsrchr, wcsrtombs, wcsspn, wcsstr, wcstod, wcstof, wcstoimax,
    wcstok, wcstol, wcstold, wcstoll, wcstombs, wcstoul, wcstoull, wcstoumax, wcswcs, wcswidth, wcsxfrm,
    wctob, wctomb, wctrans, wctype, wcwidth, w_getmntent, w_getpsent, w_getpsent64, w_ioctl, __w_pioctl,
    wmemchr, wmemcmp, wmemcpy, wmemmove, wmemset, wordexp, wordfree, wprintf, write, __writedown, writev,
    wscanf, __wsinit, w_statfs, w_statvfs, y0, y1, yn
    ;

    static ZosFuncSymbols valueOfOrNull(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static final SequenceLayout LAYOUT = MemoryLayout.sequenceLayout(
            values().length, ValueLayout.ADDRESS);
}
