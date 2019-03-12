package com.openbankproject.adapter.actor;


import akka.actor.UntypedActor;

public class ResultActor extends UntypedActor {


    @Override
    public void onReceive(Object message) {
        System.out.println("接收到：message = [" + message + "]");
    }
}