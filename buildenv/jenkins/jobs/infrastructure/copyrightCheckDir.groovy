/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2019, 2019 All Rights Reserved
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

def VERBOSE = ""
def OUTPUT = ""

timeout(time: 6, unit: 'HOURS') {
    stage('Copyright Check') {
        node ('worker') {
            if ( params.ghprbGhRepository == "") {
                echo "Repository to check not specified.  Rerun with the ghprbGhRepository parameter pointing to a valid git repository."
                sh 'exit 1'
            }
            if ( params.Verbose == "1") {
                VERBOSE="-v"
            }
            timestamps {
                checkout scm
                OUTPUT = sh (script: "sh buildenv/jenkins/jobs/infrastructure/copyrightCheckDir.sh REPO=${params.ghprbGhRepository}", returnStdout: true).trim()
                echo OUTPUT
            } // timestamps
        } // node
    } // stage
} // timeout

