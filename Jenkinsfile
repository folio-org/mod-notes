
@Library('folio_jenkins_shared_libs@modgql-1') _

buildMvn {
  publishModDescriptor = 'yes'
  publishAPI = 'yes'
  mvnDeploy = 'yes'

  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ | exit 1'
    }
  }
}

