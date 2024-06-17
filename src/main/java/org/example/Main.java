package org.example;

import org.example.model.Participant;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            new Participant().startClient();
        } catch (IOException exception) {
            System.out.println("Falha na inicialização do participante" + exception.getMessage());
        }
    }
}