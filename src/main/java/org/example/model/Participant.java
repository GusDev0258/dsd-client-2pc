package org.example.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Participant {
    private final int VOTE_COMMIT = 1;
    private final int VOTE_ABORT = 2;
    private ServerSocket serverSocket;
    private Socket socket;
    private BufferedReader inReader;
    private PrintWriter outWriter;
    private String coordinatorDecision = "";
    private String innerDecision = "";
    private int port;

    public void startClient() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite a porta que deseja iniciar este participante: ");
        this.port = Integer.parseInt(scanner.nextLine());
        try {
            this.serverSocket = new ServerSocket(this.port);
        } catch (IOException exception) {
            System.out.println("Não foi possível iniciar este participante" + exception.getMessage());
        }
        System.out.print("Endereço do coordenador: ");
        String coordinatorHost = scanner.nextLine();
        System.out.print("Porta do coordenador: ");
        int coordinatorPort = Integer.parseInt(scanner.nextLine());

        try {
            this.socket = new Socket(coordinatorHost, coordinatorPort);
            this.inReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.outWriter = new PrintWriter(socket.getOutputStream(), true);

            new Thread(new CoordinatorListener()).start();

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            Future<?> future = executor.submit(() -> {
                try {
                    while (true) {
                        String message = inReader.readLine();
                        if (message == null) {
                            break;
                        }
                        handleCoordinatorMessage(message);
                    }
                } catch (IOException exception) {
                    System.out.println("Falha ao ler mensagem do coordenador, a conexão com o coordenador falhou " + exception.getMessage());
                    this.innerDecision = Message.ABORT;
                }
            });

            executor.schedule(() -> {
                if (!future.isDone()) {
                    future.cancel(true);
                    handleCoordinatorMessage(Message.GLOBAL_ABORT);
                }
            }, 1, TimeUnit.MINUTES);

            executor.shutdown();
        } catch (IOException expcetion) {
            System.out.println("Não foi possível conectar ao coordenador: " + expcetion.getMessage());
        }
    }

    private void handleCoordinatorMessage(String message) {
        Scanner scanner = new Scanner(System.in);
        switch (message) {
            case Message.REQUEST:
                System.out.println("Recebida uma solicitação de voto do coordenador. Escolha como prosseguir: ");
                System.out.println("1. VOTE_COMMIT");
                System.out.println("2. VOTE_ABORT");

                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                Future<?> future = executor.submit(() -> {
                    int userChoice = scanner.nextInt();
                    if (userChoice == VOTE_COMMIT) {
                        sendMessage(Message.COMMIT);
                        this.innerDecision = Message.COMMIT;
                    } else if (userChoice == VOTE_ABORT) {
                        sendMessage(Message.ABORT);
                        this.innerDecision = Message.ABORT;
                    } else {
                        System.out.println("Opção inválida, abordando commmit");
                        sendMessage(Message.COMMIT);
                        this.innerDecision = Message.ABORT;
                    }
                });

                executor.schedule(() -> {
                    if (!future.isDone()) {
                        future.cancel(true);
                        sendMessage(Message.ABORT);
                        this.innerDecision = Message.ABORT;
                    }
                }, 1, TimeUnit.MINUTES);
                executor.shutdown();
                break;
            case Message.GLOBAL_COMMIT:
                System.out.println("Transação Confirmada.");
                this.coordinatorDecision = Message.GLOBAL_COMMIT;
                break;
            case Message.GLOBAL_ABORT:
                System.out.println("Transação Abortada.");
                this.coordinatorDecision = Message.GLOBAL_ABORT;
                break;
        }
    }

    private void sendMessage(String message) {
        this.outWriter.println(message);
    }

    private class CoordinatorListener implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Socket participantSocket = serverSocket.accept();
                    BufferedReader participantReader =
                            new BufferedReader(new InputStreamReader(participantSocket.getInputStream()));
                    String message = participantReader.readLine();
                    handleCoordinatorMessage(message);
                    participantSocket.close();
                }
            } catch (IOException exception) {
                System.out.println("Problemas ao lidar com as mensagem do coordenador " + exception.getMessage());
            }
        }
    }
}
