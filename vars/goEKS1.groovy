def call(Map configMap){    
    pipeline {
        agent any
        // {
        //     node{
        //         label 'GO-AGENT'
        //     }
        // }
        // environment { 
        //     packageVersion = ''
                
        // }
        // environment{
        //     SONAR_HOME= tool "Sonar-scanner"
        // }
        environment{
            nexusURL = '172.31.71.176:8081'
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
                        def fileContents = readFile 'main.go' // Read the content of main.go
                        // Extract the application version from the file content
                        def packageVersion = fileContents =~ /Version\s*=\s*"([^"]*)"/
                        // Check if the version is found
                        if (packageVersion) {
                            // Access the captured version group (group 1)
                            packageVersion = packageVersion[0][1]
                            echo "Application version: $packageVersion"
                        } else {
                            error "Application version not found in main.go"
                        }                        
                    }   
                }
            }
            stage('Install Dependencies') {
                steps {
                    sh """
                        go build -o ${configMap.component}
                    """
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

            // stage("SonarQube Code Analysis"){
            //     steps{
            //         withSonarQubeEnv("Sonar-scanner"){
            //             sh "$SONAR_HOME/bin/sonar-scanner -Dsonar.projectName=${configMap.component} -Dsonar.projectKey=${configMap.component}"
            //         }
            //     }
            // }

            // stage("OWASP Dependency Check"){
            //     steps{
            //         dependencyCheck additionalArguments: '--scan ./', odcInstallation: 'DC'
            //         dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            //     }
            // }
        
            // stage("SonarQube Code Quality Gates"){
            //     steps{
            //         timeout(time: 5, unit: "MINUTES"){
            //             waitForQualityGate abortPipeline: false
            //         }
            //     }
            // }
            
            // stage("Trivy filesystem Scan"){
            //     steps{
            //         sh "trivy fs --format table -o trivy-fs-report.html ."
            //     }
            // }


            stage('Build') {
                steps {
                    sh """
                        ls -ltr
                        zip -q -r ${configMap.component}.zip ./* -x ".git" -x "*.zip"
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
            stage('Deploy') {
                when {
                    expression {
                        params.Deploy
                    }
                }
                steps {
                    script {
                        def params = [
                            string(name: 'version', value:"$packageVersion"),
                            string(name: 'environment', value:"dev")
                            // booleanParam(name: 'create', value: "${params.Deploy}")
                        ]
                        build job: "../${configMap.component}-deploy", wait: true, parameters: params
                        
                    }
                }
            }       


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