/*
 *  Copyright (c) 2019-2021, Arm Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)
 */

import hudson.plugins.git.GitSCM
import hudson.scm.NullSCM
import org.jenkinsci.plugins.workflow.multibranch.SCMVar

def set_common_environment() {
    /* Do moderately parallel builds. This overrides the default in all.sh
     * which is to do maximally parallel builds (-j). Massively parallel builds
     * can cause load spikes which cause other builds to lag and time out, so
     * avoid that. Do somewhat parallel builds, not just sequential builds,
     * so that the CI has a chance to detect related makefile bugs. */
    env.MAKEFLAGS = '-j2'
    env.VERBOSE_LOGS=1
}

void set_pr_environment(String target_repo, boolean is_production) {
    set_common_environment()
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = target_repo
    if (is_production) {
        set_common_pr_production_environment()
        if (env.IS_RESTRICTED) {
            env.FRAMEWORK_REPO = 'git@github.com:Mbed-TLS/mbedtls-framework-restricted.git'
            env.FRAMEWORK_FALLBACK_REPO = 'git@github.com:Mbed-TLS/mbedtls-framework.git'
            env.TF_PSA_CRYPTO_REPO = 'git@github.com:Mbed-TLS/TF-PSA-Crypto-restricted.git'
            env.TF_PSA_CRYPTO_FALLBACK_REPO = 'git@github.com:Mbed-TLS/TF-PSA-Crypto.git'
            env.MBED_TLS_REPO = 'git@github.com:Mbed-TLS/mbedtls-restricted.git'
        } else {
            env.FRAMEWORK_REPO = 'git@github.com:Mbed-TLS/mbedtls-framework.git'
            env.TF_PSA_CRYPTO_REPO = 'git@github.com:Mbed-TLS/TF-PSA-Crypto.git'
            env.MBED_TLS_REPO = 'git@github.com:Mbed-TLS/mbedtls.git'
        }
    } else {
        env.CHECKOUT_METHOD = 'parametrized'
    }
}

/** <p> Extract the repository URL from the {@link GitSCM} object returned by
 *  {@link SCMVar scm} and set {@code env.GITHUB_ORG}, {@code env.GITHUB_REPO}
 *  and {@code env.IS_RESTRICTED} accordingly if they were not initialized
 *  before. The repository URL refers to the repository containing the job's
 *  Jenkinsfile, so MbedTLS/mbedtls-test for all release/parametrized jobs and
 *  the PR's target repository for PR jobs. </p>
 *
 *  <p> If the branch {@code scm} points to is deleted before this method is called
 *  (eg. when a PR exits the merge queue), it degenerates to an instance of {@link NullSCM},
 *  and the variables above remain uninitialized. </p>
 */
void parse_scm_repo() {
    if (!env.GITHUB_ORG && scm instanceof GitSCM) {
        def (org, repo) = scm.userRemoteConfigs[0].url.replaceFirst(/.*:/, '').split('/')[-2..-1]
        repo = repo.replaceFirst(/\.git$/, '')
        env.GITHUB_ORG = org
        env.GITHUB_REPO = repo
        if (repo ==~ /.*-restricted/) {
            env.IS_RESTRICTED = 'true'
        }
    }
}

def set_common_pr_production_environment() {
    env.CHECKOUT_METHOD = 'scm'
    /* The credentials here are the SSH credentials for accessing the repositories.
       They are defined at {JENKINS_URL}/credentials
       This is a temporary workaround, this should really be set in the Jenkins job configs */
    env.GIT_CREDENTIALS_ID = common.is_open_ci_env ? "mbedtls-github-ssh" : "742b7080-e1cc-41c6-bf55-efb72013bc28"
    if (env.BRANCH_NAME ==~ /PR-\d+-merge/) {
        env.RUN_ABI_CHECK = 'true'
    } else {
        env.RUN_FREEBSD = 'true'
        env.RUN_WINDOWS_TEST = 'true'
        env.RUN_ALL_SH = 'true'
    }
    parse_scm_repo()
}

def set_tls_release_environment() {
    set_common_environment()
    env.JOB_TYPE = 'release'
    env.TARGET_REPO = 'tls'
    env.CHECKOUT_METHOD = 'parametrized'
}

def set_mbed_os_example_pr_environment(example, is_production) {
    set_common_environment()
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = 'example'
    switch (example) {
        case 'TLS':
            env.TEST_MBED_OS_AUTHCRYPT_EXAMPLE = 'true'
            env.TEST_MBED_OS_BENCHMARK_EXAMPLE = 'true'
            env.TEST_MBED_OS_HASHING_EXAMPLE = 'true'
            env.TEST_MBED_OS_TLS_CLIENT_EXAMPLE = 'true'
            break
        case 'Crypto':
            env.TEST_MBED_OS_CRYPTO_EXAMPLES = 'true'
            break
        case 'ATECC608A':
            env.TEST_MBED_OS_ATECC608A_EXAMPLES = 'true'
            break
        default:
            throw new Exception("No example specified")
    }
    if (is_production) {
        env.CHECKOUT_METHOD = 'scm'
    } else {
        env.CHECKOUT_METHOD = 'parametrized'
    }
}
