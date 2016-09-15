#!groovy
def repo = "anon-files"
def dockerUser = "discoenv"

node {
    try {
        stage "Build"
        checkout scm

        sh 'git rev-parse HEAD > GIT_COMMIT'
        git_commit = readFile('GIT_COMMIT').trim()
        echo git_commit

        sh 'grep "(defproject" project.clj | sed -E \'s/^[^"]*"([^"]+)".*$/\\1/\' > VERSION'
        version = readFile('VERSION').trim()
        echo version

        dockerRepo = "${dockerUser}/${repo}:${env.BRANCH_NAME}"

        sh "docker build --rm --build-arg git_commit=${git_commit} --build-arg version=${version} -t ${dockerRepo} ."


        try {
            stage "Test"
            dockerTestRunner = "test-${env.BUILD_TAG}"
            sh "docker run --rm --name ${dockerTestRunner} --entrypoint 'lein' ${dockerRepo} test"
        } finally {
            sh returnStatus: true, script: "docker kill ${dockerTestRunner}"
            sh returnStatus: true, script: "docker rm ${dockerTestRunner}"
        }


        stage "Docker Push"
        sh "docker push ${dockerRepo}"
    } catch (InterruptedException e) {
        currentBuild.result = "ABORTED"
        slackSend color: 'warning', message: "ABORTED: job '${env.JOB_NAME}' [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        throw e
    } catch (e) {
        currentBuild.result = "FAILED"
        sh "echo ${e}"
        slackSend color: 'danger', message: "FAILED: job '${env.JOB_NAME}' [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        throw e
    }
}
