@Library ('folio_jenkins_shared_libs@docker-auth') _

buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
  publishAPI = true
  runLintRamlCop = true
  doKubeDeploy = true
  buildNode =  'jenkins-agent-java11'

  doDocker = {
    buildJavaDocker {
      publishMaster = true
      healthChk = true
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ || exit 1'
    }
  }
}

