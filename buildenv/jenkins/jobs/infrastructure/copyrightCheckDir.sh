#!/bin/sh
# ===========================================================================
# (c) Copyright IBM Corp. 2019, 2021 All Rights Reserved
# ===========================================================================
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# IBM designates this particular file as subject to the "Classpath" exception
# as provided by IBM in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, see <http://www.gnu.org/licenses/>.
#
# ===========================================================================

trace () {
  if [ "$VERBOSE" = "1" ]; then
    echo $1
  fi
  return
}

log () {
  echo $1
  return
}

check () {
  if [ ! "$ROOTDIR" = "" ]; then
    case "$1" in
      $ROOTDIR/*) CHECK=1;;
      *) return 0;;
    esac
  fi
  trace "Checking file $1"

  ERROR=0

  CHECK=0
  case "$1" in
     # Comment out to check the test directory files.
    test/*)
      trace "Ignoring $1 because it appears to match case parameter expansion test/*"
      CHECK=0;;
    # Ignore binary files
    *.gif)
      trace "Ignoring $1 because it appears to match case parameter expansion *.gif"
      CHECK=0;;
    *.ini)
      trace "Ignoring $1 because it appears to match case parameter expansion *.ini"
      CHECK=0;;
    *.jpg)
      trace "Ignoring $1 because it appears to match case parameter expansion *.jpg"
      CHECK=0;;
    *.jpeg)
      trace "Ignoring $1 because it appears to match case parameter expansion *.jpeg"
      CHECK=0;;
    *.ico)
      trace "Ignoring $1 because it appears to match case parameter expansion *.ico"
      CHECK=0;;
    *.bmp)
      trace "Ignoring $1 because it appears to match case parameter expansion *.bmp"
      CHECK=0;;
    *.png)
      trace "Ignoring $1 because it appears to match case parameter expansion *.png"
      CHECK=0;;
    *.wav)
      trace "Ignoring $1 because it appears to match case parameter expansion *.wav"
      CHECK=0;;
    *.md)
      trace "Ignoring $1 because it appears to match case parameter expansion *.md"
      CHECK=0;;
    *.icu)
      trace "Ignoring $1 because it appears to match case parameter expansion *.icu"
      CHECK=0;;
    *.so)
      trace "Ignoring $1 because it appears to match case parameter expansion *.so"
      CHECK=0;;
    *.dll)
      trace "Ignoring $1 because it appears to match case parameter expansion *.dll"
      CHECK=0;;
    *.exe)
      trace "Ignoring $1 because it appears to match case parameter expansion *.exe"
      CHECK=0;;
    *.bin)
      trace "Ignoring $1 because it appears to match case parameter expansion *.bin"
      CHECK=0;;
    *.zip)
      trace "Ignoring $1 because it appears to match case parameter expansion *.zip"
      CHECK=0;;
    *.jar)
      trace "Ignoring $1 because it appears to match case parameter expansion *.jar"
      CHECK=0;;
    *.class)
      trace "Ignoring $1 because it appears to match case parameter expansion *.class"
      CHECK=0;;
    *.cer)
      trace "Ignoring $1 because it appears to match case parameter expansion *.cer"
      CHECK=0;;
    *.ser)
      trace "Ignoring $1 because it appears to match case parameter expansion *.ser"
      CHECK=0;;
    *) CHECK=1;;
  esac

  if [ "$CHECK" = '0' ]; then
    return 0
  fi

  # File needs checking

  # If we are checking this file or the pull request copyright checker limit
  # the number of lines processed otherwise, since all the copyright search
  # strings are in these files, errors would be reported.
  case "$1" in
    *copyrightCheckDir.sh)
      trace "Checking copyright checker file $1"
      MAX_LINES=80;;
    *copyrightCheck)
      trace "Checking copyright checker file $1"
      MAX_LINES=80;;
    *) MAX_LINES=400;;
  esac

  # Some source files have special characters such as the copyright symbol.
  # Linux grep interprets these as binary files unless the '-a' option is used
  GREP=grep
  uname -a | grep -q Linux && GREP="grep -a"

  FOUND_ORACLE_DESIGNATES=$(head -n $MAX_LINES "$1" | $GREP -n "Oracle designates" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_ORACLE_DESIGNATES" ] && FOUND_ORACLE_DESIGNATES=0
  if [ "$FOUND_ORACLE_DESIGNATES" -gt 0 ]; then
    trace "We have found the Oracle designates on line $FOUND_ORACLE_DESIGNATES in file: $1"
  fi

  FOUND_IBM_DESIGNATES=$(head -n $MAX_LINES "$1" | $GREP -n "IBM designates" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_IBM_DESIGNATES" ] && FOUND_IBM_DESIGNATES=0
  if [ "$FOUND_IBM_DESIGNATES" -gt 0 ]; then
    trace "We have found the IBM designates on line $FOUND_IBM_DESIGNATES in file: $1"
  fi

  FOUND_IBM_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n ".*Copyright IBM Corp.*All Rights Reserved.*" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_IBM_COPYRIGHT" ] && FOUND_IBM_COPYRIGHT=0
  if [ "$FOUND_IBM_COPYRIGHT" -gt 0 ]; then
    trace "We have found the IBM Copyright on line $FOUND_IBM_COPYRIGHT in file: $1"
  fi

  FOUND_IBM_PORTIONS_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n ".*Portions Copyright.*IBM Corporation.*" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_IBM_PORTIONS_COPYRIGHT" ] && FOUND_IBM_PORTIONS_COPYRIGHT=0
  if [ "$FOUND_IBM_PORTIONS_COPYRIGHT" -gt 0 ]; then
    trace "We have found the IBM Portions Copyright on line $FOUND_IBM_PORTIONS_COPYRIGHT in file: $1"
  fi

  FOUND_APACHE_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n ".*Licensed to the Apache Software Foundation.*" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_APACHE_COPYRIGHT" ] && FOUND_APACHE_COPYRIGHT=0
  if [ "$FOUND_APACHE_COPYRIGHT" -gt 0 ]; then
    trace "We have found the Apache Software Foundation Copyright on line $FOUND_APACHE_COPYRIGHT in file: $1"
  fi

  FOUND_BSD_OR_MIT_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n -i "BSD license" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_BSD_OR_MIT_COPYRIGHT" ] && FOUND_BSD_OR_MIT_COPYRIGHT=0
  if [ "$FOUND_BSD_OR_MIT_COPYRIGHT" -gt 0 ]; then
    trace "We have found a BSD, MIT or other Copyright on line $FOUND_BSD_OR_MIT_COPYRIGHT in file: $1"
  else
    FOUND_BSD_OR_MIT_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n -i "MIT license" | head -n 1 | cut -d: -f1)
    [ -z "$FOUND_BSD_OR_MIT_COPYRIGHT" ] && FOUND_BSD_OR_MIT_COPYRIGHT=0
    if [ "$FOUND_BSD_OR_MIT_COPYRIGHT" -gt 0 ]; then
      trace "We have found a BSD, MIT or other Copyright on line $FOUND_BSD_OR_MIT_COPYRIGHT in file: $1"
    else
      FOUND_BSD_OR_MIT_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n "Redistribution and use in source and binary forms" | head -n 1 | cut -d: -f1)
      [ -z "$FOUND_BSD_OR_MIT_COPYRIGHT" ] && FOUND_BSD_OR_MIT_COPYRIGHT=0
      if [ "$FOUND_BSD_OR_MIT_COPYRIGHT" -gt 0 ]; then
        trace "We have found a BSD or MIT style Copyright on line $FOUND_BSD_OR_MIT_COPYRIGHT in file: $1"
      else
        FOUND_BSD_OR_MIT_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n "PROVIDED.*AS IS" | head -n 1 | cut -d: -f1)
        [ -z "$FOUND_BSD_OR_MIT_COPYRIGHT" ] && FOUND_BSD_OR_MIT_COPYRIGHT=0
        if [ "$FOUND_BSD_OR_MIT_COPYRIGHT" -gt 0 ]; then
          trace "We have found a BSD or MIT style Copyright on line $FOUND_BSD_OR_MIT_COPYRIGHT in file: $1"
        fi
      fi
    fi
  fi

  FOUND_ORACLE_PROPRIETARY_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n -i "ORACLE PROPRIETARY" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_ORACLE_PROPRIETARY_COPYRIGHT" ] && FOUND_ORACLE_PROPRIETARY_COPYRIGHT=0
  if [ "$FOUND_ORACLE_PROPRIETARY_COPYRIGHT" -gt 0 ]; then
    trace "We have found an Oracle Proprietary on line $FOUND_ORACLE_PROPRIETARY_COPYRIGHT in file: $1"
  fi

  FOUND_ORACLE_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n "Copyright (c).*Oracle" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_ORACLE_COPYRIGHT" ] && FOUND_ORACLE_COPYRIGHT=0
  if [ "$FOUND_ORACLE_COPYRIGHT" -gt 0 ]; then
    trace "We have found an Oracle Copyright on line $FOUND_ORACLE_COPYRIGHT in file: $1"
  fi
  FOUND_COPYRIGHT=0
  if [ "$FOUND_IBM_COPYRIGHT" -eq 0 ]; then
    if [ "$FOUND_APACHE_COPYRIGHT" -eq 0 ]; then
      if [ "$FOUND_BSD_OR_MIT_COPYRIGHT" -eq 0 ]; then
        if [ "$FOUND_ORACLE_COPYRIGHT" -eq 0 ]; then
          FOUND_COPYRIGHT=$(head -n $MAX_LINES "$1" | $GREP -n "Copyright" | head -n 1 | cut -d: -f1)
        fi
      fi
    fi
  fi
  [ -z "$FOUND_COPYRIGHT" ] && FOUND_COPYRIGHT=0
  if [ "$FOUND_COPYRIGHT" -gt 0 ]; then
    trace "We have found a different Copyright on line $FOUND_COPYRIGHT in file: $1"
  fi

  FOUND_IBM_CPE=0
  FOUND_ORACLE_CPE=0
  FOUND_CPE=$(head -n $MAX_LINES "$1" | $GREP -n " \"Classpath\"" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_CPE" ] && FOUND_CPE=0
  if [ "$FOUND_CPE" -eq 0 ]; then
    FOUND_CPE=$(head -n $MAX_LINES "$1" | $GREP -n -i " Classpath Exception" | head -n 1 | cut -d: -f1)
    [ -z "$FOUND_CPE" ] && FOUND_CPE=0
  fi
  if [ "$FOUND_CPE" -gt 0 ]; then
    trace "We have found a Classpath Exception on line $FOUND_CPE in file: $1"
    if [ "$FOUND_IBM_DESIGNATES" -gt 0 ]; then
      FOUND_IBM_CPE=$FOUND_CPE
      trace "We have found the IBM CPE on line $FOUND_ORACLE_CPE in file: $1"
    else
      if [ "$FOUND_ORACLE_DESIGNATES" -gt 0 ]; then
        FOUND_ORACLE_CPE=$FOUND_CPE
        trace "We have found the Oracle CPE on line $FOUND_ORACLE_CPE in file: $1"
      fi
    fi
  fi

  FOUND_GPLV2=$(head -n $MAX_LINES "$1" | $GREP -n "GNU General Public License" | head -n 1 | cut -d: -f1)
  [ -z "$FOUND_GPLV2" ] && FOUND_GPLV2=0
  if [ "$FOUND_GPLV2" -gt 0 ]; then
    trace "We have found GPLv2 on line $FOUND_GPLV2 in file: $1"
  fi

  FOUND_NON_IBM_COPYRIGHT=0
  [ "$FOUND_APACHE_COPYRIGHT" -gt 0 ]     && FOUND_NON_IBM_COPYRIGHT=1
  [ "$FOUND_BSD_OR_MIT_COPYRIGHT" -gt 0 ] && FOUND_NON_IBM_COPYRIGHT=1
  [ "$FOUND_ORACLE_COPYRIGHT" -gt 0 ]     && FOUND_NON_IBM_COPYRIGHT=1
  [ "$FOUND_COPYRIGHT" -gt 0 ]            && FOUND_NON_IBM_COPYRIGHT=1
  if [ "$FOUND_NON_IBM_COPYRIGHT" -eq 1 ]; then
    trace "We have found a non IBM copyright"
  fi

  # Check to see whether the file is in the built JDK
  IN_JDK=1
  case "$1" in
    test/*)
      trace "$1 deemed not to be in the built JDK because it matches case parameter expansion test/*"
      IN_JDK=0;;
    bin/*)
      trace "$1 deemed not to be in the built JDK because it matches case parameter expansion bin/*"
      IN_JDK=0;;
    make/autoconf/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/autoconf/*"
      IN_JDK=0;;
    make/hotspot/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/hotspot/*)"
      IN_JDK=0;;
    make/data/hotspot-symbols/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/data/hotspot-symbols/*)"
      IN_JDK=0;;
    make/langtools/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/langtools/*"
      IN_JDK=0;;
    make/nashorn/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/nashorn/*"
      IN_JDK=0;;
    make/scripts/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/scripts/*"
      IN_JDK=0;;
    make/templates/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/templates/*"
      IN_JDK=0;;
    src/utils/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion src/utils/*"
      IN_JDK=0;;
    */test/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion */test/*"
      IN_JDK=0;;
    *configure)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion *configure"
      IN_JDK=0;;
    *.1)
      trace "$1 deemed not to be in the built JDK because it matches case parameter expansion *.1"
      IN_JDK=0;;
    common/*) # common/autoconf and common/bin on jdk8 only
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion common/*"
      IN_JDK=0;;
    # langtools structure on jdk8 only
    langtools/make/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion langtools/make/*"
      IN_JDK=0;;
    # nashorn structure on jdk8 only
    nashorn/bin/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion nashorn/bin/*"
      IN_JDK=0;;
    nashorn/buildtools/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion nashorn/buildtools/*"
      IN_JDK=0;;
    nashorn/docs/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion nashorn/docs/*"
      IN_JDK=0;;
    nashorn/make/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion nashorn/make/*"
      IN_JDK=0;;
    make/ide/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/ide/*"
      IN_JDK=0;;
    jdk/test/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion jdk/test/*"
      IN_JDK=0;;
    make/jdk/src/classes/build/tools/*)
      trace  "$1 deemed not to be in the built JDK because it matches case parameter expansion make/jdk/src/classes/build/tools/*"
      IN_JDK=0;;  
    */share/classes/sun/security/util/math/intpoly/FieldGen.jsh)
      trace  "$1 is not part of the built JDK"
      IN_JDK=0;;
    *) IN_JDK=1;;
  esac

  # We have pulled the info from the file, now do all the checks

  # Files with no copyright......
  if [ "$FOUND_IBM_COPYRIGHT" -eq 0 ] && [ "$FOUND_NON_IBM_COPYRIGHT" -eq 0 ]; then
    if [ "$FOUND_ORACLE_CPE" -gt 0 ]; then
      trace "$1: Found Oracle classpath exception but no copyright"
    else
      if [ "$FOUND_GPLV2" -gt 0 ]; then
        trace "$1: Found GPLv2 but no copyright"
      else
        trace "$1 has no copyright"
      fi
    fi
  fi

  CLOSED=0
  case "$1" in
    closed/*) CLOSED=1;;
    *) CLOSED=0;;
  esac


  if [ "$CLOSED" -eq 1 ]; then
    trace "$1 is in the closed directory"
    if [ "$FOUND_IBM_COPYRIGHT" -eq 0 ]; then
      # closed/openjdk-tag.gmk is updated by the automated jobs which merge the openjdk repositories
      # into the extensions repository and should not have a copyright.
      case "$1" in
        closed/openjdk-tag.gmk)
          trace "$1 has no copyright which is correct";;
      *)
        log "E002: $1: Basic IBM Copyright is missing"
        ERROR=1;;
      esac
    else
      if [ "$FOUND_NON_IBM_COPYRIGHT" -gt 0 ] && [ "$FOUND_GPLV2" -gt 0 ] && [ "$FOUND_CPE" -gt 0 ]; then
        if [ "$FOUND_IBM_COPYRIGHT" -lt "$FOUND_ORACLE_COPYRIGHT" ] || [ "$FOUND_IBM_COPYRIGHT" -lt "$FOUND_BSD_OR_MIT_COPYRIGHT" ] || [ "$FOUND_IBM_COPYRIGHT" -lt "$FOUND_APACHE_COPYRIGHT" ] || [ "$FOUND_IBM_COPYRIGHT" -lt "$FOUND_COPYRIGHT" ]; then
          log "E001: $1: IBM Copyright is not after the existing copyright"
          ERROR=1
        fi
      else
        # The file is in the 'closed' directory and doesn't contain 
        # Oracle copyright with GPLv2 and Classpath Exception so should
        # have IBM copyright with GPLv2 and CE at the top of the file
        if [ "$FOUND_IBM_COPYRIGHT" -gt 5 ]; then
          log "E003: $1: IBM Copyright with GPLv2 and IBM Classpath Exception should be at the top of the file"
          ERROR=1
        fi
        if [ "$FOUND_GPLV2" -eq 0 ]; then
          log "E004: $1: IBM Copyright should contain the GPLv2 license"
          ERROR=1
        fi
        if [ "$FOUND_IBM_CPE" -eq 0 ]; then
          log "E005: $1: IBM Copyright should contain the IBM Classpath Exception"
          ERROR=1
        fi
        if [ "$FOUND_ORACLE_CPE" -gt 0 ]; then
          log "E006: $1: IBM Copyright should not contain the Oracle Classpath Exception"
          ERROR=1
        fi
      fi
    fi
  fi

  if [ "$CLOSED" -eq 0 ]; then
    trace "The file is NOT in the closed directory"
    # Check that if the file has an IBM copyright or an IBM Portions copyright then it is
    # positioned correctly in the file.
    # If we don't have a non IBM copyright, i.e. Oracle, Apache, BSD, MIT etc., or GPLv2 header
    # or classpath exception....
    if [ "$FOUND_NON_IBM_COPYRIGHT" -eq 0 ] && [ "$FOUND_GPLV2" -eq 0 ] && [ "$FOUND_CPE" -eq 0 ]; then
      trace "File $1 has no non IBM copyright, GPLv2 or classpath exception"
      # Is the file a user configurable file?  If so it shouldn't have a copyright.
      case "$1" in
        *META-INF/*)
          trace "$1 deemed to be a user configurable file because it matches parameter expansion *META-INF/*"
          NOT_CONFIG_FILE=0;;
        */Changelog)
          trace "$1 deemed to be a user configurable file because it matches parameter expansion */Changelog"
          NOT_CONFIG_FILE=0;;
        *.policy)
          trace "$1 deemed to be a user configurable file because it matches parameter expansion *.policy"
          NOT_CONFIG_FILE=0;;
        *.security)
          trace "$1 deemed to be a user configurable file because it matches parameter expansion *.security"
          NOT_CONFIG_FILE=0;;
        *.plist)
          trace "$1 deemed to be a user configurable file because it matches parameter expansion *.plist"
          NOT_CONFIG_FILE=0;;
        *) NOT_CONFIG_FILE=1;;
      esac
      if [ "$NOT_CONFIG_FILE" -eq 0 ]; then
        if [ "$FOUND_IBM_COPYRIGHT" -gt 0 ]; then
          log "E007: $1: IBM Copyright should NOT be used in this file as it is a user config file"
          ERROR=1
        fi
      else
        # The file is not a user configuration file.
        # If it has been changed it should have IBM portions.
        # Check to see if the IBM Portions copyright if present is at top of file.
        if [ "$FOUND_IBM_PORTIONS_COPYRIGHT" -gt 3 ]; then
          log "E009: $1: IBM Portions Copyright is not at top of the file"
          ERROR=1
        fi
      fi
    fi # [ "$FOUND_NON_IBM_COPYRIGHT" -eq 0 ] && [ "$FOUND_GPLV2" -eq 0 ] && [ "$FOUND_CPE" -eq 0 ]

    # If we don't have a non IBM copyright we were able to identify, i.e. Oracle, Apache, BSD, MIT etc.,
    # and we have but we have a GPLv2 header but no classpath exception, and the file is built into
    # the jdk....
    if [ "$FOUND_NON_IBM_COPYRIGHT" -eq 0 ] && [ "$FOUND_GPLV2" -gt 0 ] && [ "$FOUND_CPE" -eq 0 ] && [ "$IN_JDK" -ne 0 ]; then
      log "E010: File $1: GPLv2 is present but Classpath exception is missing"
      ERROR=1
    fi

    # If we have a non IBM copyright, a GPLv2 header but no classpath exception, and the file is built
    # into the jdk.....
    if [ "$FOUND_NON_IBM_COPYRIGHT" -ne 0 ] && [ "$FOUND_GPLV2" -gt 0 ] && [ "$FOUND_CPE" -eq 0 ] && [ "$IN_JDK" -ne 0 ]; then
      trace "$1 has no classpath exception but is in the JDK binary"
      if [ "$1" = "LICENSE" ]; then
        if [ "$FOUND_IBM_CPE" -eq 0 ]; then
          log "E011: $1: LICENSE file does not contain IBM designated Classpath Exception"
          ERROR=1
        fi
      else
        log "E012: $1: GPLv2 is present but Classpath exception is missing"
        ERROR=1
      fi
    fi

    # If we have a non IBM copyright, a GPLv2 header and a classpath exception.....
    if [ "$FOUND_NON_IBM_COPYRIGHT" -ne 0 ] && [ "$FOUND_GPLV2" -gt 0 ] && [ "$FOUND_CPE" -gt 0 ]; then
      if [ "$1" = "LICENSE" ]; then
        if [ "$FOUND_IBM_CPE" -eq 0 ]; then
          log "E014: $1: LICENSE file does not contain IBM designated Classpath Exception"
          ERROR=1
        fi
      fi
    fi

    # If we have a non IBM copyright which is not GPLv2.....
    if [ "$FOUND_NON_IBM_COPYRIGHT" -ne 0 ] && [ "$FOUND_GPLV2" -eq 0 ]; then
      # If the file also has an IBM copyright, check that it is after
      # any existing copyright
      if [ "$FOUND_IBM_COPYRIGHT" -gt 0 ]; then
        if [ "$FOUND_IBM_COPYRIGHT" -le "$FOUND_ORACLE_COPYRIGHT" ] || [ "$FOUND_IBM_COPYRIGHT" -le "$FOUND_BSD_OR_MIT_COPYRIGHT" ] || [ "$FOUND_IBM_COPYRIGHT" -le "$FOUND_APACHE_COPYRIGHT" ] || [ "$FOUND_IBM_COPYRIGHT" -le "$FOUND_COPYRIGHT" ]; then
          log "E016: $1: IBM copyright is not after the existing copyright"
          ERROR=1
        fi
      fi
    fi

    # If there is an Oracle Proprietary copyright....
    if [ "$FOUND_ORACLE_PROPRIETARY_COPYRIGHT" -ne 0 ]; then
      log "E017: File $1: Found Oracle Proprietary copyright"
      ERROR=1
    fi
  fi # CLOSED

  if [ $ERROR -eq 1 ]; then
    grep -q "$1" $TEMPFILE
    if [ "$?" -eq 0 ]; then
      echo "Found $1 in known failures file $TEMPFILE, not treating as error"
      w=$((w+1))
    else
      echo "Did not find $1 in known failures file $TEMPFILE, treating as error"
      e=$((e+1))
    fi
  fi

}

# Main logic here
VERBOSE="0"
ARGS_ERROR=0
REPO_NAME=
ROOTDIR=

for ARG in "$@"
do
  VAR=$(echo $ARG | cut -f1 -d=)
  VAL=$(echo $ARG | cut -f2 -d=)
  case $VAR in
    REPO)    REPO=$VAL;;
    VERBOSE) VERBOSE=$VAL;;
    ROOTDIR) ROOTDIR=$VAL;;
    *)       echo Unrecognised argument \"$VAR\"
             ARGS_ERROR=1;;
  esac
done

if [ ! "$VERBOSE" = "0" ] && [ ! "$VERBOSE" = "1" ]; then
  echo Unrecognised VERBOSE value \"$VERBOSE\"
  ARGS_ERROR=1
fi
if [ "$REPO" = "" ]; then
  echo REPO not specified
  ARGS_ERROR=1
fi

if [ $ARGS_ERROR -eq 1 ]; then
  echo
  echo Usage:
  echo
  echo copyrightCheck.sh REPO=git_repository ROOTDIR=root_directory VERBOSE=1
  echo REPO:    a github repository. Mandatory
  echo ROOTDIR: check only this durectory and subdirectories
  echo VERBOSE: output logging
  echo
  echo Example: to check the entire repository github.com:ibmruntimes/openj9-openjdk-jdk
  echo copyrightCheck.sh REPO=ibmruntimes/openj9-openjdk-jdk
  echo
  echo Example: to check the closed directory in repository github.com:ibmruntimes/openj9-openjdk-jdk with verbose output:
  echo copyrightCheck.sh REPO=ibmruntimes/openj9-openjdk-jdk ROOTDIR=closed VERBOSE=1
  echo
  echo Use ROOTDIR in conjunction with VERBOSE to limit output
  exit 1
fi

REPO_NAME=$REPO
case $REPO_NAME in
  *.git) REPO_DIR="$REPO_NAME";;
  *) REPO_DIR="$REPO_NAME.git";;
esac

REPO_URL="https://github.com/$REPO_DIR"

PWD=`pwd`
WORKDIR="$PWD/workdir/$REPO_DIR"
if [ -d $WORKDIR ]; then
  echo Working directory $WORKDIR already exists, deleting it.
  rm -rf $WORKDIR
fi

mkdir -p $WORKDIR
log "`date` Running git clone --depth=1 \"$REPO_URL\" \"$WORKDIR\""
git clone --depth=1 "$REPO_URL" "$WORKDIR"
cd "$WORKDIR" || {
    log "ERROR: $WORKDIR does not exist after cloning $REPO_NAME. Check git clone output."
    exit 1
}
log "`date` Clone finished, checking files...."

# Create a file containing all the known files with errors we want to temporarily ignore.
# These files are reported as errors but do not cause the script to exit non-zero.

TEMPFILE=$PWD/copyrightCheck.known.failures
echo "Currently excluded files......." >$TEMPFILE

echo "\n# openj9-openjdk-jdk known failures" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_db/libjvm_db.c" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_db/libjvm_db.h" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_dtrace/jvm_dtrace.c" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_dtrace/jvm_dtrace.h" >>$TEMPFILE
echo "src/jdk.internal.le/windows/classes/jdk/internal/org/jline/terminal/impl/jna/win/IntByReference.java" >>$TEMPFILE
echo "src/jdk.internal.le/windows/classes/jdk/internal/org/jline/terminal/impl/jna/win/Kernel32Impl.java" >>$TEMPFILE
echo "src/jdk.internal.le/windows/classes/jdk/internal/org/jline/terminal/impl/jna/win/LastErrorException.java" >>$TEMPFILE
echo "src/jdk.internal.le/windows/classes/jdk/internal/org/jline/terminal/impl/jna/win/Pointer.java" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/HotSpotGraalManagement.java" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/HotSpotGraalRuntimeMBean.java" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/JMXServiceProvider.java" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/package-info.java" >>$TEMPFILE

echo "\n# openj9-openjdk-jdk11 known failures" >>$TEMPFILE
echo "make/mapfiles/libjsig/mapfile-vers-solaris" >>$TEMPFILE
echo "make/data/license-templates/gpl-header" >>$TEMPFILE
echo "make/mapfiles/libjvm_db/mapfile-vers" >>$TEMPFILE
echo "make/mapfiles/libjvm_dtrace/mapfile-vers" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_db/libjvm_db.c" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_db/libjvm_db.h" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_dtrace/jvm_dtrace.c" >>$TEMPFILE
echo "src/java.base/solaris/native/libjvm_dtrace/jvm_dtrace.h" >>$TEMPFILE
echo "src/java.base/unix/native/libjsig/jsig.c" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/HotSpotGraalManagement.java" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/HotSpotGraalRuntimeMBean.java" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/JMXServiceProvider.java" >>$TEMPFILE
echo "src/jdk.internal.vm.compiler.management/share/classes/org.graalvm.compiler.hotspot.management/src/org/graalvm/compiler/hotspot/management/package-info.java" >>$TEMPFILE

echo "\n# openj9-openjdk-jdk8 known failures" >>$TEMPFILE
echo "jdk/make/mapfiles/libjfr/mapfile-vers" >>$TEMPFILE
echo "jdk/make/src/native/add_gnu_debuglink/add_gnu_debuglink.c" >>$TEMPFILE
echo "jdk/make/src/native/fix_empty_sec_hdr_flags/fix_empty_sec_hdr_flags.c" >>$TEMPFILE
echo "jdk/src/macosx/native/jobjc/JObjC.xcodeproj/default.pbxuser" >>$TEMPFILE
echo "jdk/src/share/classes/org/jcp/xml/dsig/internal/dom/DOMXPathFilter2Transform.java" >>$TEMPFILE
echo "src/java.xml.crypto/share/classes/org/jcp/xml/dsig/internal/dom/DOMXPathFilter2Transform.java" >>$TEMPFILE
echo "src/java.xml.crypto/share/classes/org/jcp/xml/dsig/internal/dom/XMLDSigRI.java" >>$TEMPFILE
echo "jdk/src/share/classes/org/jcp/xml/dsig/internal/dom/XMLDSigRI.java" >>$TEMPFILE
echo "jdk/test/java/awt/Frame/DecoratedExceptions/DecoratedExceptions.java" >>$TEMPFILE
# The following file refers to the license in other source files. That license is GPL v3 without classpath exception, but the files
# themselves are not actually present in the openjdk source repository
echo "src/java.smartcardio/unix/native/libj2pcsc/MUSCLE/COPYING" >>$TEMPFILE
echo "jdk/src/solaris/native/sun/security/smartcardio/MUSCLE/COPYING" >>$TEMPFILE
# The following file was written by IBM, and then later some oracle code has been added to this file, 
# After legal consideration, decesion was top add oracle copyrights where ever the oracle code is written which result IBM copyrights on top and later oracle.
echo "closed/adds/jdk/src/windows/native/jdk/crypto/jniprovider/NativeCrypto_md.c" >>$TEMPFILE


cat $TEMPFILE

echo ""

a=0
e=0
w=0
for FILE in `git ls-files`; do
  check $FILE $COPYRIGHTIGNORE_FILE
  a=$((a+1))
  if [ $((a%1000)) -eq 0 ]; then
    log "`date` Processed $a files"
  fi
done
log "`date` Processed $a files"
log "`date` Found $w files with errors also in the known failures list"
log "`date` Found $e files with errors"

rm $TEMPFILE

if [ $e -eq 0 ]; then
  exit 0
fi

exit 1
