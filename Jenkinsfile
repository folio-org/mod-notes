pipeline {

   environment {
      docker_repository = 'folioci'
      docker_image = "${env.docker_repository}/mod-notes"
   }
    
   agent {
      node {
         label 'folio-jenkins-slave-docker'
      }
   }
    
   stages {
      stage('Prep') {
         steps {
            script {
               currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
            }

            sendNotifications 'STARTED'
            step([$class: 'WsCleanup'])
         }
      }
 
      stage('Checkout') {
         steps {          
            checkout([
               $class: 'GitSCM',
               branches: scm.branches,
               extensions: scm.extensions + [[$class: 'SubmoduleOption', 
                                                       disableSubmodules: false, 
                                                       parentCredentials: false, 
                                                       recursiveSubmodules: true, 
                                                       reference: '', 
                                                       trackingSubmodules: false]], 
               userRemoteConfigs: scm.userRemoteConfigs
            ])

            echo " Checked out $env.BRANCH_NAME"
         }   
      } 
        
      stage('Build') {
         steps {
            script {
               def pom = readMavenPom file: 'pom.xml' 
               env.POM_VERSION = pom.version
            }

            echo "$env.POM_VERSION"

            withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node', 
                      maven: 'Maven on Ubuntu Docker Slave Node', 
                      options: [junitPublisher(disabled: false, 
                                ignoreAttachments: false), 
                                artifactsPublisher(disabled: false)]) {
                    
               sh 'mvn clean integration-test'
            }
         }
      }
   }  

   post { 
      always { 
        sendNotifications currentBuild.result
      }
   }  

}
     
