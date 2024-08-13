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
            packageVersion = ''
            nexusURL = '172.31.71.176:8081'
            ACCOUNT_ID = "608782704145"
            REGION = "us-east-1"
        }
        tools {
            //jfrog 'jfrog-cli'
            go 'golang'
        }
        
        options {
            timeout(time: 1, unit: 'HOURS')
            disableConcurrentBuilds()
            ansiColor('xterm')
        }
        parameters {
            booleanParam(name: 'Deploy', defaultValue: false, description: 'Toggle this value')
            string(name: 'projectName', defaultValue: "${configMap.component}", description: 'Name of the project')
        }

        stages {
            stage('Get the version') {
                steps { 
                    script{
                        def fileContents = readFile 'main.go' // Read the content of main.go
                        // Extract the application version from the file content
                        packageVersion = fileContents =~ /Version\s*=\s*"([^"]*)"/
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

            stage('snyk test') {
                steps{
                    snykSecurity(
                        snykInstallation: 'mysnyk',
                        snykTokenId: '4290f677-9a6b-4e6c-b0fc-f551763382f0',
                }

            }
            stage('Build') {
                steps {
                    sh """
                        ls -ltr
                        zip -q -r ${configMap.component}.zip ./* -x ".git" -x "*.zip"
                        ls -ltr
                    
                    """
                }
            }
            // stage('Publish build info') { //Artifactory and jfrog plugins need to be installed
            //     steps {
            //         rtServer (
            //         id: 'server-1',
            //         url: 'http://100.26.49.102:8082/artifactory',
            //         credentialsId: 'jfrog-auth',
            //         //bypassProxy: true
            //         )
            //         rtUpload (
            //         serverId: 'server-1',
            //         spec: '''{
            //             "files": [
            //                 {
            //                 "pattern": "frontend.zip",
            //                 "target": "frontend"
            //                 }
            //             ]
            //         }'''
            //         )
            //     }
            // }
            // stage('Publish Artifact') { // nexus artifact uploader plugin
            //     steps {
            //         nexusArtifactUploader(
            //             nexusVersion: 'nexus3',
            //             protocol: 'http',
            //             nexusUrl: "${nexusURL}",
            //             //nexusUrl: '172.31.71.176:8081',
            //             //nexusURL: pipelineGlobals.nexusURL(),
            //             groupId: 'com.hipstershop',
            //             //version: '1.0.0',
            //             version: "${packageVersion}",
            //             repository: "${configMap.component}",
            //             credentialsId: 'nexus-auth', // store nexus credentials
            //             artifacts: [
            //                 [artifactId: "${configMap.component}",
            //                 classifier: '',
            //                 file: "${configMap.component}.zip",
            //                 type: 'zip']
            //             ]
            //         )
            //     }
            // }
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

            stage('Docker Image Build') {
                steps{
                    script{
                        sh """
                            docker system prune -f
                            docker build -t srikanthhg/${configMap.component}:${packageVersion} .
                        """
                    }
                }
            } 

            stage('Docker Image Scan') {
                steps {
                    sh"""
                        trivy image --format table -o trivy-image-report.html srikanthhg/${configMap.component}:${packageVersion} 
                    """
                }
            }

            stage('Publish Docker Image') { //docke and docker pipeline plugin installed
                steps{
                    script{
                        withDockerRegistry(credentialsId: 'docker-auth') {
                            sh "docker push srikanthhg/${configMap.component}:${packageVersion}"
                        }
                    }    
                }
            }  
            stage('AWS login'){ //aws cli install
                steps{
                    script{
                        sh """
                        aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com
                        """
                    }
                }
            }  

            stage('ECR Push'){
                steps{
                    script{
                        sh """
                        docker tag srikanthhg/${configMap.component}:${packageVersion} ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${configMap.component}:${packageVersion}
                        docker push ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${configMap.component}:${packageVersion}
                        """ 
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