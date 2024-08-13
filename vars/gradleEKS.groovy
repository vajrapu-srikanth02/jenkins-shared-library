def call(Map configMap){    
    pipeline {
        agent any
        environment { 
            packageVersion = ''    
            nexusURL = '172.31.71.176:8081'
        }
        tools {
            //gradle 'gradle-tool'
            //When you run these commands using ./gradlew, you are using the Gradle Wrapper (gradlew), which is a script that allows you to run Gradle tasks without needing to have Gradle installed globally on your system. The wrapper script ensures that the version of Gradle specified in your project is used, making your build more reproducible across different environments.
            jdk 'javarun' // temurin plugin installed
        }
        options {
            timeout(time: 1, unit: 'HOURS')
            disableConcurrentBuilds()
            ansiColor('xterm')
        }
        parameters {
            booleanParam(name: 'Deploy', defaultValue: false, description: 'Toggle this value')
        }

        stages {
            stage('Get the version') {
                steps { 
                    script{
                        def version = sh(returnStdout: true, script: "cat build.gradle | grep -o 'version = [^,]*'").trim()
                        sh "echo Project in version value: $version"
                        packageVersion = version.split(/=/)[1]
                        sh "echo Application version: $packageVersion"                   
                    }
                }   
            }
          
            stage('Unit testing') {
                    steps {
                        sh """
                            echo "unit tests will run here" 
                        """
                    }
                }
            stage('Sonar scan') { // sonar-scanner is the command, it will read sonar-project properties and start scanning
                steps {
                    sh """
                        echo "sonar-scanner"
                    """
                }
            }
            stage('Build application') {
                steps {
                    sh """
                        chmod +x gradlew
                        ./gradlew downloadRepos
                        ./gradlew installDist
                    """
                }
            }
            stage('Build') {
                steps {
                    sh """
                        ls -ltr
                        zip -q -r ${configMap.component}.zip ./build
                        ls -ltr
                    """
                }
            }
        stage('Publish Artifact') { // nexus artifact uploader plugin
            steps {
                nexusArtifactUploader(
                    nexusVersion: 'nexus3',
                    protocol: 'http',
                    nexusUrl: "${nexusURL}",
                    //nexusUrl: '172.31.74.236:8081',
                    //nexusURL: pipelineGlobals.nexusURL(),
                    groupId: 'com.hipstershop',
                    //version: '1.0.0',
                    version: "${packageVersion}",
                    repository: "${configMap.component}",
                    credentialsId: 'nexus-auth', // store nexus credentials
                    artifacts: [
                        [artifactId: "${configMap.component}",
                        classifier: '',
                        file: "${configMap.component}.zip",
                        type: 'zip']
                    ]
                )
            }
        }
        // stage('Deploy') {
        //     when {
        //         expression {
        //             params.Deploy
        //         }
        //     }
        //     steps {
        //         script {
        //             def params = [
        //                 string(name: 'version', value:"$packageVersion"),
        //                 string(name: 'environment', value:"dev")
        //                 // booleanParam(name: 'create', value: "${params.Deploy}")
        //             ]
        //             build job: "../${configMap.component}-deploy", wait: true, parameters: params
                    
        //         }
        //     }
        // }  
        }     
        post { 
            always { 
                echo 'I will always say Hello again!'
                deleteDir()
            }
            failure { 
                echo 'this runs when pipeline is failed, used generally to send some alerts'
            }
            success{
                echo 'I will say Hello when pipeline is success'
            }
        }
    }
}