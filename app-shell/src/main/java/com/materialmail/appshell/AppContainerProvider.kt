package com.materialmail.appshell

/** 每个壳（:app / :pro:app）的 Application 实现它，Widget/Receiver 通过它拿容器。 */
interface AppContainerProvider {
    val container: AppContainer
}