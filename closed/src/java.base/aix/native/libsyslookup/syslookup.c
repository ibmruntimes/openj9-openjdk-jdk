/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2023, 2025 All Rights Reserved
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

#include <fstab.h>
#include <setjmp.h>
#include <string.h>
#include <strings.h>

/* The function list inlines the libc functions which
 * are missing in loading the default library.
 */
static void *funcs[] = {
    &bcopy, &endfsent, &getfsent, &getfsfile, &getfsspec, &longjmp,
    &memcpy, &memmove, &setfsent, &setjmp, &siglongjmp, &strcat,
    &strcmp, &strcpy, &strncat, &strncpy
};

__attribute__((visibility("default"))) void **
funcs_addr(void)
{
    return funcs;
}
