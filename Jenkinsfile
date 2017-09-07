#!groovy
@Library('github.com/wooga/atlas-jenkins-pipeline@0.0.3') _

pipeline {
    agent {
        label 'windows'
    }

    environment {
        artifactoryCredentials            = credentials('artifactory_publish')
        nugetkey                          = credentials('artifactory_deploy')
        TRAVIS_JOB_NUMBER                 = "${BUILD_NUMBER}.WIN"
        GITHUB                            = credentials('github_integration')
        ATLAS_GITHUB_INTEGRATION_USER     = "${GITHUB_USR}"
        ATLAS_GITHUB_INTEGRATION_PASSWORD = "${GITHUB_PSW}"
    }

    stages {
        stage('Preparation') {
            steps {
                sendSlackNotification "STARTED", true
            }
        }

        stage('Test') {
            steps {
                gradleWrapper "check"
            }
        }
    }

    post {
        always {
            gradleWrapper "jacocoTestReport"
            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'build/reports/jacoco/test/html',
                reportFiles: 'index.html',
                reportName: 'Coverage',
                reportTitles: ''
            ])

            sendSlackNotification currentBuild.result, true
            junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
            gradleWrapper "clean"
        }
    }
}