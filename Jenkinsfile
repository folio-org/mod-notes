
@Library('folio_jenkins_shared_libs@folio-932') _

buildMvn {
  publishModDescriptor = 'yes'
  publishAPI = 'yes'
  mvnDeploy = 'yes'

  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      healthChk = 'no'
    }
  }
}

