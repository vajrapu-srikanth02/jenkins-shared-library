def call(Map configMap){    
    pipeline {
        agent any
        environment { 
            packageVersion = ''    
            nexusURL = '172.31.71.176:8081'
        }
        // tools {
        //     msbuild 'dotnetapp'
        // }
        tools {
            dotnetsdk 'dotnetapp' // Installed plugin .NET SDK Support Version 
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
                        def csprojFile = readFile 'src/cartservice.csproj'
                        def versionRegex = /<Version>(.*?)<\/Version>/
                        def match = (csprojFile =~ versionRegex)
                    
                        if (match) {
                            packageVersion = match[0][1]
                            
                            echo "packageVersion: ${packageVersion}"
                            // You can use the 'version' variable for further processing
                        } else {
                            error "Failed to extract version from .csproj file"
                        }              
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

            stage('Restore') {
                steps {
                    script {
                        def dotnetCmd = "dotnet restore"
                        sh "${dotnetCmd}"
                    }
                }
            }
        
            stage('Build') {
                steps {
                    script {
                        def dotnetCmd = "dotnet build"
                        sh "${dotnetCmd}"
                    }
                }
            }
        
            // stage('Test') {
            //     steps {
            //         script {
            //             def dotnetCmd = "dotnet test"
            //             sh "${dotnetCmd}"
            //         }
            //     }
            // }
        
            stage('Publish') {
                steps {
                    script {
                        def dotnetCmd = "dotnet publish src/cartservice.csproj -c Release -o ./cartservice"
                        sh "${dotnetCmd}"
                    }
                    archiveArtifacts artifacts: '**/cartservice/*.dll', fingerprint: true
                }
            }
            stage('Buildd') {
                steps {
                    sh """
                        ls -ltr
                        zip -q -r ${configMap.component}.zip ./${configMap.component}/* -x ".git" -x "*.zip"
                        ls -ltr
                        unzip -l ${configMap.component}.zip 
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
                //deleteDir()
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