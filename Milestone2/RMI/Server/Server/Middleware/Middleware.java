package Server.Middleware;

import Server.Transactions.*;
import Server.LockManager.*;
import Server.Interface.*;
import Server.Common.*;
import Server.Middleware.ServerConfig;
import java.util.Vector;
import java.rmi.RemoteException;
import java.rmi.ConnectException;

import java.rmi.NotBoundException;
import java.util.*;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Middleware extends ResourceManager {

    protected IResourceManager m_flightResourceManager = null;
    protected IResourceManager m_carResourceManager = null;
    protected IResourceManager m_roomResourceManager = null;

    protected static ServerConfig s_flightServer;
    protected static ServerConfig s_carServer;
    protected static ServerConfig s_roomServer;

    protected MiddlewareTM tm;
    protected LockManager lm;
    private int timetolive = 30;

    public Middleware(String p_name)
    {
        super(p_name);
        tm = new MiddlewareTM(timetolive);
        lm = new LockManager();
    }

    public int start() throws RemoteException{
        int xid  = tm.start();
        Trace.info("Starting transaction - " + xid);
        return xid;
    }

    public boolean commit(int xid) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        System.out.print("Commit transaction:" + xid);

        checkTransaction(id);
        Transaction t = tm.readActiveData(xid);

        Set<String> resources = t.getResourceManagers();

        if (resources.contains("Flight"))
            m_flightResourceManager.commit(xid);

        if (resources.contains("Car"))
            m_flightResourceManager.commit(xid);

        if (resources.contains("Room"))
            m_flightResourceManager.commit(xid);

        if (resources.contains("Customer")) {

            RMHashMap m = t.getData();

            synchronized (m_data) {
                Set<String> keyset = m.keySet();
                for (String key : keyset) {
                    System.out.print("Write:(" + key + "," + m.get(key) + ")");
                    m_data.put(key, m.get(key));
                }
            }
        }

        // Move to inactive transactions
        tm.writeActiveData(xid, null);
        tm.writeInactiveData(xid, new Boolean(true));

        lm.UnlockAll(id);

        return true;
    }

    public boolean shutdown() throws RemoteException {
        m_flightResourceManager.shutdown();
        m_carResourceManager.shutdown();
        m_roomResourceManager.shutdown();

        new Thread() {
            @Override
            public void run() {
                System.out.print("Shutting down...");
                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    // I don't care
                }
                System.out.println("done");
                System.exit(0);
            }

        }.start();
        return true;
    }

    public boolean addFlight(int xid, int flightNum, int flightSeats, int flightPrice) throws RemoteException,TransactionAbortedException, InvalidTransactionException {
        int id = xid;

        Trace.info("addFlight - Redirect to Flight Resource Manager");
        checkTransaction(id);

        acquireLock(id, Flight.getKey(flightNum), TransactionLockObject.LockType.LOCK_WRITE);
        addResourceManagerUsed(id,"Flight");
        try {
            try {
                return m_flightResourceManager.addFlight(id, flightNum, flightSeats, flightPrice);
            } catch (ConnectException e) {
                connectServer("Flight", s_flightServer.host, s_flightServer.port, s_flightServer.name);
                return m_flightResourceManager.addFlight(id, flightNum, flightSeats, flightPrice);
            }
        } catch (TransactionAbortedException a) {
            tm.abort(id);
            lm.UnlockAll(id);
        } catch (Exception e) {
            Trace.error(e.toString());
        }
        return false;
    }

    public boolean addCars(int xid, String location, int numCars, int price) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("addCars - Redirect to Car Resource Manager");
        checkTransaction(id);

        acquireLock(id, Car.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
        addResourceManagerUsed(id,"Car");
        try {
            try {
                return m_carResourceManager.addCars(id, location, numCars, price);
            } catch (ConnectException e) {
                connectServer("Car", s_carServer.host, s_carServer.port, s_carServer.name);
                return m_carResourceManager.addCars(id, location, numCars, price);
            }
        } catch (TransactionAbortedException a) {
            tm.abort(id);
            lm.UnlockAll(id);
        } catch (Exception e) {
            Trace.error(e.toString());
        }
        return false;

    }

    public boolean addRooms(int xid, String location, int numRooms, int price) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("addRooms - Redirect to Room Resource Manager");
        checkTransaction(id);

        acquireLock(id, Room.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
        addResourceManagerUsed(id,"Room");
        try {
            try {
                return m_roomResourceManager.addRooms(id, location, numRooms, price);
            } catch (ConnectException e) {
                connectServer("Room", s_roomServer.host, s_roomServer.port, s_roomServer.name);
                return m_roomResourceManager.addRooms(id, location, numRooms, price);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
        }
        return false;

    }

    public boolean deleteFlight(int xid, int flightNum) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("deleteFlight - Redirect to Flight Resource Manager");
        checkTransaction(id);

        acquireLock(id, Flight.getKey(flightNum), TransactionLockObject.LockType.LOCK_WRITE);
        addResourceManagerUsed(id,"Flight");
        try {
            try {
                return m_flightResourceManager.deleteFlight(id, flightNum);
            } catch (ConnectException e) {
                connectServer("Flight", s_flightServer.host, s_flightServer.port, s_flightServer.name);
                return m_flightResourceManager.deleteFlight(id, flightNum);
            }
        }
        catch (Exception e) {
            Trace.error(e.toString());
            return false;
        }

    }

    public boolean deleteCars(int xid, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("deleteCars - Redirect to Car Resource Manager");
        checkTransaction(id);

        acquireLock(id, Car.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
        addResourceManagerUsed(id,"Car");
        try {
            try {
                return m_carResourceManager.deleteCars(id, location);
            } catch (ConnectException e) {
                connectServer("Car", s_carServer.host, s_carServer.port, s_carServer.name);
                return m_carResourceManager.deleteCars(id, location);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return false;
        }

    }

    public boolean deleteRooms(int xid, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("deleteRooms - Redirect to Room Resource Manager");
        checkTransaction(id);

        acquireLock(id, Room.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
        addResourceManagerUsed(id,"Room");
        try {
            try {
                return m_roomResourceManager.deleteRooms(id, location);
            } catch (ConnectException e) {
                connectServer("Room", s_roomServer.host, s_roomServer.port, s_roomServer.name);
                return m_roomResourceManager.deleteRooms(id, location);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return false;
        }

    }

    public boolean deleteCustomer(int xid, int customerID) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("RM::deleteCustomer(" + xid + ", " + customerID + ") called");
        checkTransaction(xid);

        acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Customer");
        Customer customer = (Customer)readData(xid, Customer.getKey(customerID));
        if (customer == null)
        {
            Trace.warn("RM::deleteCustomer(" + xid + ", " + customerID + ") failed--customer doesn't exist");
            return false;
        }
        else {
            acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
            // Increase the reserved numbers of all reservable items which the customer reserved.
            RMHashMap reservations = customer.getReservations();
            for (String reservedKey : reservations.keySet()) {
                String type = reservedKey.split("-")[0];
                ReservedItem reserveditem = customer.getReservedItem(reservedKey);
                if (type.equals("flight")) {
                    acquireLock(xid, reservedKey, TransactionLockObject.LockType.LOCK_WRITE);
                    addResourceManagerUsed(id,"Flight");
                    m_flightResourceManager.removeReservation(xid, customerID, reserveditem.getKey(), reserveditem.getCount());
                } else if (type.equals("car")) {
                    acquireLock(xid, reservedKey, TransactionLockObject.LockType.LOCK_WRITE);
                    addResourceManagerUsed(id,"Car");
                    m_carResourceManager.removeReservation(xid, customerID, reserveditem.getKey(), reserveditem.getCount());
                } else if (type.equals("room")) {
                    acquireLock(xid, reservedKey, TransactionLockObject.LockType.LOCK_WRITE);
                    addResourceManagerUsed(id,"Room");
                    m_roomResourceManager.removeReservation(xid, customerID, reserveditem.getKey(), reserveditem.getCount());
                } else
                    Trace.error("RM::deleteCustomer(" + xid + ", " + customerID + ") failed--reservedKey (" + reservedKey + ") wasn't of expected type.");

            }
            // Remove the customer from the storage
            removeData(xid, customer.getKey());
            Trace.info("RM::deleteCustomer(" + xid + ", " + customerID + ") succeeded");
            return true;
        }

    }

    public int queryFlight(int xid, int flightNumber) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("queryFlight - Redirect to Flight Resource Manager");
        checkTransaction(id);

        acquireLock(id, Flight.getKey(flightNumber), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Flight");
        try {
            try {
                return m_flightResourceManager.queryFlight(id, flightNumber);
            } catch (ConnectException e) {
                connectServer("Flight", s_flightServer.host, s_flightServer.port, s_flightServer.name);
                return m_flightResourceManager.queryFlight(id, flightNumber);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return -1;
        }
    }

    public int queryCars(int xid, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("queryCars - Redirect to Car Resource Manager");
        checkTransaction(id);

        acquireLock(id, Car.getKey(location), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Car");
        try {
            try {
                return m_carResourceManager.queryCars(id, location);
            } catch (ConnectException e) {
                connectServer("Car", s_carServer.host, s_carServer.port, s_carServer.name);
                return m_carResourceManager.queryCars(id, location);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return -1;
        }
    }

    public int queryRooms(int xid, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("queryRooms - Redirect to Room Resource Manager");
        checkTransaction(id);

        acquireLock(id, Room.getKey(location), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Room");
        try {
            try {
                return m_roomResourceManager.queryRooms(id, location);
            } catch (ConnectException e) {
                connectServer("Room", s_roomServer.host, s_roomServer.port, s_roomServer.name);
                return m_roomResourceManager.queryRooms(id, location);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return -1;
        }
    }

    public int queryFlightPrice(int xid, int flightNumber) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("queryFlightPrice - Redirect to Flight Resource Manager");
        checkTransaction(id);

        acquireLock(id, Flight.getKey(flightNumber), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Flight");
        try {
            try {
                return m_flightResourceManager.queryFlightPrice(id, flightNumber);
            } catch (ConnectException e) {
                connectServer("Flight", s_flightServer.host, s_flightServer.port, s_flightServer.name);
                return m_flightResourceManager.queryFlightPrice(id, flightNumber);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return -1;
        }
    }

    public int queryCarsPrice(int xid, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("queryCarsPrice - Redirect to Car Resource Manager");
        checkTransaction(id);

        acquireLock(id, Car.getKey(location), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Car");
        try {
            try {
                return m_carResourceManager.queryCarsPrice(id, location);
            } catch (ConnectException e) {
                connectServer("Car", s_carServer.host, s_carServer.port, s_carServer.name);
                return m_carResourceManager.queryCarsPrice(id, location);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return -1;
        }
    }

    public int queryRoomsPrice(int xid, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("queryRoomsPrice - Redirect to Room Resource Manager");
        checkTransaction(id);

        acquireLock(id, Room.getKey(location), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Room");
        try {
            try {
                return m_roomResourceManager.queryRoomsPrice(id, location);
            } catch (ConnectException e) {
                connectServer("Room", s_roomServer.host, s_roomServer.port, s_roomServer.name);
                return m_roomResourceManager.queryRoomsPrice(id, location);
            }
        } catch (Exception e) {
            Trace.error(e.toString());
            return -1;
        }
    }

    public boolean reserveFlight(int xid, int customerID, int flightNumber) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        String key = Flight.getKey(flightNumber);

        Trace.info("RM::reserveFlight(" + xid + ", customer=" + customerID + ", " + key + ") called" );
        checkTransaction(xid);
        // Check customer exists

        acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(xid,"Customer");
        Customer customer = (Customer)readData(xid, Customer.getKey(customerID));
        if (customer == null)
        {
            Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + flightNumber + ")  failed--customer doesn't exist");
            return false;
        }

        acquireLock(xid, key, TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(xid,"Flight");
        int price = m_flightResourceManager.itemsAvailable(xid, key, 1);

        if (price < 0) {
            Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + flightNumber + ")  failed--item unavailable");
            return false;
        }
        acquireLock(xid, key, TransactionLockObject.LockType.LOCK_WRITE);
        if (m_flightResourceManager.reserveFlight(xid, customerID, flightNumber)) {
            acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
            customer.reserve(key, String.valueOf(flightNumber), price);
            writeData(xid, customer.getKey(), customer);
            return true;
        }
        Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + flightNumber + ")  failed--Could not reserve item");
        return false;

    }

    public boolean reserveCar(int xid, int customerID, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        String key = Car.getKey(location);

        Trace.info("RM::reserveCar(" + xid + ", customer=" + customerID + ", " + key + ") called" );
        checkTransaction(xid);
        // Check customer exists

        acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Customer");
        Customer customer = (Customer)readData(xid, Customer.getKey(customerID));
        if (customer == null)
        {
            Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + location + ")  failed--customer doesn't exist");
            return false;
        }

        acquireLock(xid, key, TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Car");
        int price = m_carResourceManager.itemsAvailable(xid, key, 1);

        if (price < 0) {
            Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + location + ")  failed--item unavailable");
            return false;
        }

        acquireLock(xid, key, TransactionLockObject.LockType.LOCK_WRITE);
        if (m_carResourceManager.reserveCar(xid, customerID, location)) {
            acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
            customer.reserve(key, location, price);
            writeData(xid, customer.getKey(), customer);
            return true;
        }
        Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + location + ")  failed--Could not reserve item");
        return false;

    }

    public boolean reserveRoom(int xid, int customerID, String location) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        String key = Room.getKey(location);

        Trace.info("RM::reserveRoom(" + xid + ", customer=" + customerID + ", " + key + ") called" );
        checkTransaction(xid);
        // Check customer exists

        acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Customer");
        Customer customer = (Customer)readData(xid, Customer.getKey(customerID));
        if (customer == null)
        {
            Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + location + ")  failed--customer doesn't exist");
            return false;
        }

        acquireLock(xid, key, TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Room");
        int price = m_roomResourceManager.itemsAvailable(xid, key, 1);

        if (price < 0) {
            Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + location + ")  failed--item unavailable");
            return false;
        }

        acquireLock(xid, key, TransactionLockObject.LockType.LOCK_WRITE);
        if (m_roomResourceManager.reserveRoom(xid, customerID, location)) {
            acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
            customer.reserve(key, location, price);
            writeData(xid, customer.getKey(), customer);
            return true;
        }
        Trace.warn("RM::reserveItem(" + xid + ", " + customerID + ", " + location + ")  failed--Could not reserve item");
        return false;
    }

    public boolean bundle(int xid, int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("RM::bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ") called" );
        checkTransaction(xid);

        acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_READ);
        addResourceManagerUsed(id,"Customer");
        Customer customer = (Customer)readData(xid, Customer.getKey(customerID));
        if (customer == null)
        {
            Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--customer doesn't exist");
            return false;
        }
        synchronized (customer) {
            HashMap<String, Integer> countMap = countFlights(flightNumbers);
            HashMap<Integer, Integer> flightPrice = new HashMap<Integer, Integer>();
            int carPrice;
            int roomPrice;

            synchronized (m_flightResourceManager) {

                if (car && room) {
                    synchronized (m_carResourceManager) {
                        synchronized (m_roomResourceManager) {

                            // Check flight availability
                            for (String key : countMap.keySet()) {
                                int keyInt;

                                try {
                                    keyInt = Integer.parseInt(key);
                                } catch (Exception e) {
                                    Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--could not parse flightNumber");
                                    return false;
                                }
                                acquireLock(xid, Flight.getKey(keyInt), TransactionLockObject.LockType.LOCK_READ);
                                addResourceManagerUsed(id,"Flight");
                                int price = m_flightResourceManager.itemsAvailable(xid, Flight.getKey(keyInt), countMap.get(key));

                                if (price < 0) {
                                    Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--flight-" + key + " doesn't have enough spots");
                                    return false;
                                } else {
                                    flightPrice.put(keyInt, price);
                                }
                            }
                            acquireLock(xid, Car.getKey(location), TransactionLockObject.LockType.LOCK_READ);
                            addResourceManagerUsed(id,"Car");
                            carPrice = m_carResourceManager.itemsAvailable(xid, Car.getKey(location), 1);

                            if (carPrice < 0) {
                                Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--car-" + location + " doesn't have enough spots");
                                return false;
                            }

                            acquireLock(xid, Room.getKey(location), TransactionLockObject.LockType.LOCK_READ);
                            addResourceManagerUsed(id,"Room");
                            roomPrice = m_roomResourceManager.itemsAvailable(xid, Room.getKey(location), 1);

                            if (roomPrice < 0) {
                                Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--room-" + location + " doesn't have enough spots");
                                return false;
                            }

                            acquireLock(xid, Room.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
                            m_roomResourceManager.reserveRoom(xid, customerID, location);

                            acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
                            addResourceManagerUsed(id,"Customer");
                            customer.reserve(Room.getKey(location), location, roomPrice);

                            writeData(xid, customer.getKey(), customer);

                            acquireLock(xid, Car.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
                            m_carResourceManager.reserveCar(xid, customerID, location);

                            // Already have customer LOCK_WRITE
                            customer.reserve(Car.getKey(location), location, carPrice);
                            writeData(xid, customer.getKey(), customer);

                        }
                    }

                } else if (car) {
                    synchronized (m_carResourceManager) {

                        // Check flight availability
                        for (String key : countMap.keySet()) {
                            int keyInt;

                            try {
                                keyInt = Integer.parseInt(key);
                            } catch (Exception e) {
                                Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--could not parse flightNumber");
                                return false;
                            }
                            acquireLock(xid, Flight.getKey(keyInt), TransactionLockObject.LockType.LOCK_READ);
                            addResourceManagerUsed(id,"Flight");
                            int price = m_flightResourceManager.itemsAvailable(xid, Flight.getKey(keyInt), countMap.get(key));

                            if (price < 0) {
                                Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--flight-" + key + " doesn't have enough spots");
                                return false;
                            } else {
                                flightPrice.put(keyInt, price);
                            }
                        }
                        acquireLock(xid, Car.getKey(location), TransactionLockObject.LockType.LOCK_READ);
                        addResourceManagerUsed(id,"Car");
                        carPrice = m_carResourceManager.itemsAvailable(xid, Car.getKey(location), 1);

                        if (carPrice < 0) {
                            Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--car-" + location + " doesn't have enough spots");
                            return false;
                        }
                        acquireLock(xid, Car.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
                        m_carResourceManager.reserveCar(xid, customerID, location);

                        acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
                        addResourceManagerUsed(id,"Customer");
                        customer.reserve(Car.getKey(location), location, carPrice);
                        writeData(xid, customer.getKey(), customer);

                    }
                } else if (room) {
                    synchronized (m_roomResourceManager) {

                        // Check flight availability
                        for (String key : countMap.keySet()) {
                            int keyInt;

                            try {
                                keyInt = Integer.parseInt(key);
                            } catch (Exception e) {
                                Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--could not parse flightNumber");
                                return false;
                            }
                            acquireLock(xid, Flight.getKey(keyInt), TransactionLockObject.LockType.LOCK_READ);
                            addResourceManagerUsed(id,"Flight");
                            int price = m_flightResourceManager.itemsAvailable(xid, Flight.getKey(keyInt), countMap.get(key));

                            if (price < 0) {
                                Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--flight-" + key + " doesn't have enough spots");
                                return false;
                            } else {
                                flightPrice.put(keyInt, price);
                            }
                        }
                        acquireLock(xid, Room.getKey(location), TransactionLockObject.LockType.LOCK_READ);
                        addResourceManagerUsed(id,"Room");
                        roomPrice = m_roomResourceManager.itemsAvailable(xid, Room.getKey(location), 1);

                        if (roomPrice < 0) {
                            Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--room-" + location + " doesn't have enough spots");
                            return false;
                        }
                        acquireLock(xid, Room.getKey(location), TransactionLockObject.LockType.LOCK_WRITE);
                        m_roomResourceManager.reserveRoom(xid, customerID, location);

                        acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
                        addResourceManagerUsed(id,"Customer");
                        customer.reserve(Room.getKey(location), location, roomPrice);
                        writeData(xid, customer.getKey(), customer);
                    }
                }
                else{
                    // Check flight availability
                    for (String key : countMap.keySet()) {
                        int keyInt;

                        try {
                            keyInt = Integer.parseInt(key);
                        } catch (Exception e) {
                            Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--could not parse flightNumber");
                            return false;
                        }
                        acquireLock(xid, Flight.getKey(keyInt), TransactionLockObject.LockType.LOCK_READ);
                        addResourceManagerUsed(id,"Flight");
                        int price = m_flightResourceManager.itemsAvailable(xid, Flight.getKey(keyInt), countMap.get(key));

                        if (price < 0) {
                            Trace.warn("RM:bundle(" + xid + ", customer=" + customerID + ", " + flightNumbers.toString() + ", " + location + ")  failed--flight-" + key + " doesn't have enough spots");
                            return false;
                        } else {
                            flightPrice.put(keyInt, price);
                        }
                    }
                }

                if (flightPrice.keySet().size() > 0) {
                    acquireLock(xid, Customer.getKey(customerID), TransactionLockObject.LockType.LOCK_WRITE);
                    addResourceManagerUsed(id,"Customer");
                }
                // Reserve flights
                for (Integer key : flightPrice.keySet()) {
                    for (int i = 0; i < countMap.get(String.valueOf(key)); i++) {
                        int price = flightPrice.get(key);

                        acquireLock(xid, Flight.getKey(key), TransactionLockObject.LockType.LOCK_WRITE);
                        m_flightResourceManager.reserveFlight(xid, customerID, key);
                        customer.reserve(Flight.getKey(key), String.valueOf(key), price);
                        writeData(xid, customer.getKey(), customer);
                    }
                }

            }
            Trace.info("RM:bundle() -- succeeded");
            return true;
        }
    }

    public String Analytics(int xid, int upperBound) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        Trace.info("RM::Analytics(" + xid + ", upperBound=" + upperBound + ") called" );
        checkTransaction(xid);

        String summary = "";
        summary += "Flight Quantities\n";
        try {
            summary += m_flightResourceManager.Analytics(xid, upperBound);
        } catch (Exception e) {
            summary += "Error in retrieving quantities\n";
        }

        summary += "Car Quantities\n";
        try {
            summary += m_carResourceManager.Analytics(xid, upperBound);
        } catch (Exception e) {
            summary += "Error in retrieving quantities\n";
        }

        summary += "Room Quantities\n";
        try {
            summary += m_roomResourceManager.Analytics(xid, upperBound);
        } catch (Exception e) {
            summary += "Error in retrieving quantities\n";
        }

        return summary;
    }

    public String Summary(int xid) throws RemoteException,TransactionAbortedException, InvalidTransactionException
    {
        int id = xid;
        String summary = "";

        for (String key: m_data.keySet()) {
            String type = key.split("-")[0];
            if (!type.equals("customer"))
                continue;
            acquireLock(xid, key, TransactionLockObject.LockType.LOCK_READ);
            addResourceManagerUsed(id,"Customer");
            Customer customer = (Customer)readData(xid, key);
            summary += customer.getSummary();

        }
        return summary;
    }

    public String getName() throws RemoteException {
        return m_name;
    }

    protected HashMap<String, Integer> countFlights(Vector<String> flightNumbers) {
        HashMap<String, Integer> map = new HashMap<String, Integer>();

        for (String flightNumber : flightNumbers) {
            if (map.containsKey(flightNumber))
                map.put(flightNumber, map.get(flightNumber) + 1);
            else
                map.put(flightNumber, 1);
        }
        return map;
    }

    protected void connectServer(String type, String server, int port, String name)
    {
        try {
            boolean first = true;
            while (true) {
                try {
                    Registry registry = LocateRegistry.getRegistry(server, port);

                    switch(type) {
                        case "Flight": {
                            m_flightResourceManager = (IResourceManager)registry.lookup(name);
                            break;
                        }
                        case "Car": {
                            m_carResourceManager = (IResourceManager)registry.lookup(name);
                            break;
                        }
                        case "Room": {
                            m_roomResourceManager = (IResourceManager)registry.lookup(name);
                            break;
                        }
                    }
                    System.out.println("Connected to '" + name + "' server [" + server + ":" + port + "/" + name + "]");
                    break;
                }
                catch (NotBoundException|RemoteException e) {
                    if (first) {
                        System.out.println("Waiting for '" + name + "' server [" + server + ":" + port + "/" + name + "]");
                        first = false;
                    }
                }
                Thread.sleep(500);
            }
        }
        catch (Exception e) {
            System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected void checkTransaction(int xid) throws TransactionAbortedException, InvalidTransactionException{
        if(tm.readActiveData(xid) != null)
            return;
        Boolean v = tm.readInactiveData(xid);
        if (v == null)
            throw new InvalidTransactionException(xid, "The transaction doesn't exist");
        else if (v.booleanValue() == true)
            throw new InvalidTransactionException(xid, "The transaction has already been committed");
        else
            throw new TransactionAbortedException(xid, "The transaction has been aborted");
    }

    protected void acquireLock(int xid, String data, TransactionLockObject.LockType lockType) throws TransactionAbortedException, InvalidTransactionException{
        try {
            boolean lock = lm.Lock(xid, data, lockType);
            if (!lock) {
                Trace.info("LM::lock(" + xid + ", " + data + ", " + lockType + ") Unable to lock");
                throw new InvalidTransactionException(xid, "LockManager-Unable to lock");
            }
        } catch (DeadlockException e) {
            Trace.info("LM::lock(" + xid + ", " + data + ", " + lockType + ") " + e.getLocalizedMessage());
            tm.abort(xid);
            lm.UnlockAll(xid);
            throw new TransactionAbortedException(xid, "The transaction has been aborted due to a deadlock");
        }
    }

    protected void addResourceManagerUsed(int xid, String resource) {
        Transaction t = tm.readActiveData(xid);
        t.addResourceManager(resource);

        try {
            try {

                switch (resource) {
                    case "Flight": {
                        m_flightResourceManager.addTransaction(xid);
                        break;
                    }
                    case "Car": {
                        m_carResourceManager.addTransaction(xid);
                        break;
                    }
                    case "Room": {
                        m_roomResourceManager.addTransaction(xid);
                        break;
                    }
                }

            } catch (ConnectException e) {

                switch (resource) {
                    case "Flight": {
                        connectServer("Flight", s_flightServer.host, s_flightServer.port, s_flightServer.name);
                        m_flightResourceManager.addTransaction(xid);
                        break;
                    }
                    case "Car": {
                        connectServer("Car", s_carServer.host, s_carServer.port, s_carServer.name);
                        m_carResourceManager.addTransaction(xid);
                        break;
                    }
                    case "Room": {
                        connectServer("Room", s_roomServer.host, s_roomServer.port, s_roomServer.name);
                        m_roomResourceManager.addTransaction(xid);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Trace.error(e.toString());
        }

    }

}