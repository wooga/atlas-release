#!groovy

/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@Library('github.com/wooga/atlas-jenkins-pipeline@0.0.3') _

pipeline {
    agent none

    stages {
        stage('Preparation') {
            agent any

            steps {
                sendSlackNotification "STARTED", true
            }
        }

        stage('check') {
            parallel {
                stage('Windows') {
                    agent {
                        label 'windows&&atlas'
                    }

                    environment {
                        COVERALLS_REPO_TOKEN                = credentials('atlas_release_coveralls_token')
                        TRAVIS_JOB_NUMBER                   = "${BUILD_NUMBER}.WIN"
                        GITHUB                              = credentials('github_integration')
                        ATLAS_GITHUB_INTEGRATION_USER       = "${GITHUB_USR}"
                        ATLAS_GITHUB_INTEGRATION_PASSWORD   = "${GITHUB_PSW}"
                    }

                    steps {
                        gradleWrapper "check"
                    }

                    post {
                        success {
                            gradleWrapper "jacocoTestReport coveralls"
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/jacoco/test/html',
                                reportFiles: 'index.html',
                                reportName: 'Coverage',
                                reportTitles: ''
                            ])
                        }

                        always {
                            junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
                        }
                    }
                }

                stage('macOS') {
                    agent {
                        label 'osx&&atlas&&secondary'
                    }

                    environment {
                        COVERALLS_REPO_TOKEN                = credentials('atlas_release_coveralls_token')
                        TRAVIS_JOB_NUMBER                   = "${BUILD_NUMBER}.MACOS"
                        GITHUB                              = credentials('github_integration')
                        ATLAS_GITHUB_INTEGRATION_USER       = "${GITHUB_USR}"
                        ATLAS_GITHUB_INTEGRATION_PASSWORD   = "${GITHUB_PSW}"
                    }

                    steps {
                        gradleWrapper "check"
                    }

                    post {
                        success {
                            gradleWrapper "jacocoTestReport coveralls"
                            publishHTML([
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/jacoco/test/html',
                                reportFiles: 'index.html',
                                reportName: 'Coverage',
                                reportTitles: ''
                            ])
                        }

                        always {
                            junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
                        }
                    }
                }
            }

            post {
                always {
                    sendSlackNotification currentBuild.result, true
                }
            }
        }
    }
}
