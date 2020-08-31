/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opentcs.kernel.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.opentcs.access.KernelRuntimeException;
import org.opentcs.access.to.order.DestinationCreationTO;
import org.opentcs.access.to.order.TransportOrderCreationTO;
import org.opentcs.components.kernel.services.ChangeTrackService;
import org.opentcs.components.kernel.services.TCSObjectService;
import org.opentcs.components.kernel.services.TransportOrderService;
import org.opentcs.customizations.kernel.GlobalSyncObject;
import org.opentcs.data.ObjectExistsException;
import org.opentcs.data.ObjectUnknownException;
import org.opentcs.data.TCSObject;
import org.opentcs.data.model.Bin;
import org.opentcs.data.model.Bin.SKU;
import org.opentcs.data.model.Location;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.TrackDefinition;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.OrderBinConstants;
import org.opentcs.data.order.OrderConstants;
import org.opentcs.data.order.OutboundOrder;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.kernel.outbound.OutboundWorkingSet;
import org.opentcs.kernel.workingset.TCSObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opentcs.components.kernel.services.OutboundOrderService;
import org.opentcs.components.kernel.services.InboundOrderService;
import org.opentcs.components.kernel.services.OrderEnableService;
import org.opentcs.data.TCSObjectEvent;
import org.opentcs.data.order.InboundOrder;

/**
 * �����������ı�׼ʵ����
 * @author Henry
 */
public class StandardOrderEnableService 
    extends AbstractTCSObjectService 
    implements OrderEnableService{
  /**
   * ��������־��¼��.
   */
  private static final Logger LOG = LoggerFactory.getLogger(StandardOrderEnableService.class);
  /**
   * һ�����ں�������ȫ�����ݳ�ͬ������.
   * ��֤���ݳصĶ��̰߳�ȫ.
   */
  private final Object globalSyncObject;
  /**
   * �ں˵�ȫ�����ݳ�.
   * ע�⣺���еĲ������Ƕ��̰߳�ȫ�ģ���Ҫʹ��ͬ����
   */
  private final TCSObjectPool objectPool;
  /**
   * ����̨.
   */
  private final OutboundWorkingSet outboundWorkingSet;
  /**
   * ���䶩������.
   */
  private final TransportOrderService tOrderService;
  /**
   * ��ⶩ������.
   */
  private final InboundOrderService inOrderService;
  /**
   * ���ⶩ������.
   */
  private final OutboundOrderService outOrderService;
  /**
   * �������.
   */
  private final ChangeTrackService changeTrackService;
  /**
   * վ���λ�þ��󣬼�¼������վ�㣨��������վ�����վ����λվ��֮������λ����Ϣ.
   */
  public static String[][] locationPosition;

  @Inject
  public StandardOrderEnableService(@GlobalSyncObject Object globalSyncObject, 
                                           TCSObjectPool objectPool,
                                           OutboundWorkingSet outboundWorkingSet,
                                           TransportOrderService tOrderService,
                                           InboundOrderService inOrderService,
                                           OutboundOrderService outOrderService,
                                           ChangeTrackService changeTrackService,
                                           TCSObjectService objectService) {
    super(objectService);
    this.globalSyncObject = globalSyncObject;
    this.objectPool = objectPool;
    this.tOrderService = tOrderService;
    this.inOrderService = inOrderService;
    this.outOrderService = outOrderService;
    this.changeTrackService = changeTrackService;
    this.outboundWorkingSet = outboundWorkingSet;
  }
  
  @Override
  public void enableOrder() {

    // ������ⶩ��
    Set<Vehicle> assignedVehicles = enableInboundOrder();
    // �ֽ���ⶩ��
    decomposeOutboundOrder(assignedVehicles);
  }
  
  /**
   * ������ⶩ��
   */
  private Set<Vehicle> enableInboundOrder(){
    Set<Vehicle> assignedVehicles = new HashSet<>();
    fetchObjects(InboundOrder.class,order -> order.hasState(InboundOrder.State.AWAIT_DISPATCH))
        .forEach(order -> {
          Location binStack = fetchObject(Location.class,order.getAssignedBinStack());
   
          if(!changeTrackService.isNoVehicleTrack(binStack.getPsbTrack())){
            Vehicle sameTrackVehicle = 
                fetchObjects(Vehicle.class, v -> v.getType().equals(Vehicle.BIN_VEHICLE_TYPE))
                    .stream()
                    .filter(psb -> inTheSameTrack(binStack.getPsbTrack(),psb))
                    .findFirst().orElse(null);
            if(sameTrackVehicle == null)
              return;
            
            Bin newBin = createBin(order, binStack.getPsbTrack());
            try{
              createTransportOrderForBin(newBin, sameTrackVehicle, order);
              inOrderService.setInboundOrderState(order.getReference(), InboundOrder.State.DISPATCHED);
              assignedVehicles.add(sameTrackVehicle);
            }
            catch(Exception exc){
              LOG.error("Error create change track order for {} failed ",order.getName(),exc);
            }
          }
          else{
            Vehicle idleVehicle = fetchObjects(Vehicle.class,this::couldProcessTransportOrder)
                .stream()
                .filter(psb -> !assignedVehicles.contains(psb))
                .sorted((psb1,psb2) -> compareDistance(psb1,psb2,binStack.getPsbTrack()))
                .findFirst().orElse(null);
            
            if(idleVehicle == null)
              return;
            Bin newBin = createBin(order, binStack.getPsbTrack());
            try{
              createTransportOrderForBin(newBin, idleVehicle, order);
              inOrderService.setInboundOrderState(order.getReference(), InboundOrder.State.DISPATCHED);
              assignedVehicles.add(idleVehicle);
            }
            catch(Exception exc){
              LOG.error("Error create change track order for {} failed ",order.getName(),exc);
            }
          }
        });
    return assignedVehicles;
  }
  /**
   * �ֽ���ⶩ��
   */
  private void decomposeOutboundOrder(Set<Vehicle> assignedVehicles){
    List<OutboundOrder> outOrders = 
        outboundWorkingSet.getWorkingSets().stream()
            .map(ref -> fetchObject(OutboundOrder.class,ref))
            // ���ݶ�������ɶ������嶩�������ȼ�����ɶ�Խ�ߣ����ȼ�Խ��
            // �����ɶ���ͬ������ݶ�����DDL������
            .sorted(Comparator.comparing(OutboundOrder::getReservedCompletion,Comparator.reverseOrder())
                .thenComparing(OutboundOrder::getDeadline))
            // ����Ҫ��������ɵĶ���.
            .filter(order -> order.getReservedCompletion() < 1.0)
            .collect(Collectors.toList());

    for(OutboundOrder outOrder : outOrders){
      // Ѱ���ʺϵ�ǰ������������
      Bin bin = getOptimalBin(outOrder);
      // �������������ڣ���Ը��������ɳ������񲢷��ɿ��г�������
      // ���������ö���������һ����Ѱ����������
      if(bin != null){
        // �������ڹ��
        Integer binTrack = bin.getPsbTrack();
        // �ҳ�����������Ŀ��г�ȥִ�д��������������
        Vehicle idleVehicle = fetchObjects(Vehicle.class, this::couldProcessTransportOrder)
            .stream()
            .filter(psb -> !assignedVehicles.contains(psb))
            .sorted((psb1,psb2) -> compareDistance(psb1,psb2,binTrack))
            .findFirst()
            .orElse(null);

        if(idleVehicle == null)
          return;
        
        // Ϊ��ǰ����Ԥ�������е�SKU
        bin = reserveSkuForOrder(bin,outOrder);

        // �鿴���������Ƿ���ҪԤ��������ʣ��δ��Ԥ����SKU
        outOrders.remove(outOrder);
        for(OutboundOrder order : outOrders){
          bin = reserveSkuForOrder(bin,order);
        }
        // �������Ԥ����ͬ�������ݳ���
        synchronized(globalSyncObject){
          objectPool.replaceObject(bin);
        }

        // Ϊ�����䴴�������������񣬲����ɿ��г���ȥִ������
        try{
          createTransportOrderForBin(bin,idleVehicle, outOrder);
        }
        catch(Exception exc){
          LOG.error("Error create change track order for {} failed ",outOrder.getName(),exc);
        }
        break;
      }
    }
  }
  
  /**
   * Ϊָ�����ⶩ��Ѱ�����ų�������.
   * @param outOrder ָ�����ⶩ��.
   * @return ���ų�������.
   */
  private Bin getOptimalBin(OutboundOrder outOrder) {
    return fetchObjects(Bin.class, 
                        b ->  b.getAssignedTransportOrder() == null 
                            && b.hasState(Bin.State.Still))
        .stream()
        // �������ڹ��Ҫô�޳���Ҫôֻ�п��г�
        .filter(bin -> 
            changeTrackService.isNoVehicleTrack(bin.getPsbTrack())
           || isIdleTrack(bin.getPsbTrack())
           )
        // ���������ȫ���򲿷�����ó��ⶩ��ʣ���SKU����
        .filter(bi -> !getMatchedSKUs(bi,outOrder.getLeftSKUs()).isEmpty())
        // ����һ������ѡ����������
        .sorted((bin1,bin2) -> findBetterBin(bin1,bin2,outOrder))
        .findFirst()
        .orElse(null);
  }
  
  private boolean isIdleTrack(int psbTrack) {
    return fetchObjects(Vehicle.class, this::couldProcessTransportOrder)
        .stream()
        .map(psb -> fetchObject(Point.class,psb.getCurrentPosition()).getPsbTrack())
        .collect(Collectors.toSet())
        .contains(psbTrack);
  }
  
  /**
   * ���ݸ�����SKU�����ҵ�����������Щ������ƥ���SKU.
   * @param bin �����ҵ�����
   * @param SKUs ������SKU����.
   * @return ��������ƥ���SKU����
   */
  private Set<SKU> getMatchedSKUs(Bin bin, Set<SKU> SKUs){
    // ���ⶩ���������
    Map<String,Double> SkuMap = SKUs.stream().collect(Collectors.toMap(SKU::getSkuID, SKU::getQuantity));
    // ������ʣ�»�δ��Ԥ����SKU
    Set<SKU> leftSKUs = bin.getNotReservedSKUs();
    // ��δ��Ԥ����SKU�붩��Ҫ���SKU������
    leftSKUs.retainAll(SKUs);
    Set<SKU> matchedSKUs = new HashSet<>();
    // ���ڽ��������õ�SKU�������п����ǳ����˶���������
    // �����Ҫ����������Ϊ ����������Ͷ��������� �еĽ�С��
    leftSKUs.forEach(sku -> {
      matchedSKUs.add(new SKU(sku.getSkuID(),getQuantityWithLimit(sku,SkuMap)));
    });
    
   return matchedSKUs;
  }
  
  /**
   * ����ָ���ĳ��ⶩ�����ҳ��������������ŵ�һ��.
   */
  private int findBetterBin(Bin bin1, Bin bin2, OutboundOrder outOrder) {
    // ���ȱȽ��������ջ���ľ��룬����ѡ����ջ������
    Integer convenience1 = getStackSize(bin1.getAttachedLocation().getName())-bin1.getBinPosition();
    Integer convenience2 = getStackSize(bin2.getAttachedLocation().getName())-bin2.getBinPosition();
    if(!Objects.equals(convenience1, convenience2))
      return convenience1.compareTo(convenience2);
    
    else{
      // �������ѡ���ܹ��ϴ�̶ȵ�������ⶩ�����������
      Set<SKU> SKUs1 = getMatchedSKUs(bin1,outOrder.getLeftSKUs());
      Set<SKU> SKUs2 = getMatchedSKUs(bin1,outOrder.getLeftSKUs());
      Double amount1 = SKUs1.stream().map(SKU::getQuantity).reduce(Double::sum).orElse(0.0);
      Double amount2 = SKUs2.stream().map(SKU::getQuantity).reduce(Double::sum).orElse(0.0);
      return amount2.compareTo(amount1);
    }
  }
  
  /**
   * ��ѯ�������Ƿ��г��ⶩ����Ҫ��SKU��������Ϊ���ⶩ��Ԥ��.
   * @param bin ����ѯ������
   * @param outOrder ���ⶩ��
   * @return ������Ԥ����֮�������.
   */
  private Bin reserveSkuForOrder(Bin bin, OutboundOrder outOrder) {
    // ��ѯ���������Ƿ��г��ⶩ����Ԥ����SKU
    Set<SKU> matchedSKUs = getMatchedSKUs(bin,outOrder.getLeftSKUs());
    if(matchedSKUs.isEmpty())
      // ����������в�û�г��ⶩ����Ҫ��SKU����ֱ������Ԥ��
      return bin;
    
    // ����У����������Ԥ����
    Map<String,Set<SKU>> reservations = bin.getReservations();
    reservations.put(outOrder.getName(), matchedSKUs);
    bin = bin.withReservations(reservations);
    
    // ���³��ⶩ����Ԥ�����
    outOrderService.reserveSKUs(outOrder.getReference(), matchedSKUs);
    outOrderService.addAssignedBin(outOrder.getReference(), bin.getReference());
    
    return bin;
  }
  
  /**
   * ��ȡ����������Ͷ����������еĽ�С��.
   */
  private static Double getQuantityWithLimit(SKU sku,Map<String, Double> Skus){
      Double limit = Skus.get(sku.getSkuID());
      Double quantity = sku.getQuantity();
      return Math.min(limit, quantity);
  }
  
  @Override
  public void createTransportOrderForBin(Bin bin, Vehicle idleVehicle, TCSObject<?> order) throws Exception {
    LOG.debug("method entry");
    
    Set<String> changeTrackOrderName = new HashSet<>();
    if(!inTheSameTrack(bin.getPsbTrack(),idleVehicle)){
      String trackOrder = changeTrackService.createChangeTrackOrder(bin,idleVehicle);        
      changeTrackOrderName.add(trackOrder);
    }
    
    TransportOrderCreationTO to;
    
    if (order instanceof OutboundOrder){
      OutboundOrder outOrder = (OutboundOrder) order;
      to = new TransportOrderCreationTO(outOrder.getName()
                                        +"-"
                                        +bin.getName()
                                        +"["
                                        +bin.getAttachedLocation().getName()
                                        +":"
                                        +bin.getBinPosition()
                                        +"]")
          .withDestinations(outBoundDestinations(bin))
          .withType(OrderConstants.TYPE_OUT_BOUND)
          .withIntendedVehicleName(idleVehicle.getName())
          .withDependencyNames(changeTrackOrderName)
          .withDeadline(outOrder.getDeadline())
          .withProperties(outOrder.getProperties());
    }
    else {
      // ���
      InboundOrder inOrder = (InboundOrder) order;
      to = new TransportOrderCreationTO(inOrder.getName()+"-")
          .withDestinations(inBoundDestinations(bin,inOrder))
          .withType(OrderConstants.TYPE_IN_BOUND)
          .withIntendedVehicleName(idleVehicle.getName())
          .withDependencyNames(changeTrackOrderName)
          .withDeadline(inOrder.getDeadline())
          .withAttachedInboundOrder(inOrder.getReference())
          .withProperties(inOrder.getProperties());
    }
    try{
      synchronized(globalSyncObject){
        objectPool.replaceObject(bin.withAssignedTransportOrder(
            tOrderService.createTransportOrder(to).getReference()));
      }
    }
    catch (ObjectUnknownException | ObjectExistsException exc) {
      throw new IllegalStateException("Unexpectedly interrupted",exc);
    }
    catch (KernelRuntimeException exc) {
      throw new KernelRuntimeException(exc.getCause());
    }
  }
  
  /**
   * �ж�ָ��������PSB���Ƿ���ָ����������.
   * @param binTrack ָ��������
   * @param vehicle ָ��������PSB��
   * @return ���ҽ�������ͳ�����ͬһ�����ʱ����{@code true}.
   */
  private boolean inTheSameTrack(int binTrack, Vehicle vehicle) {

      int vehicleTrack = fetchObject(Point.class,
                                     vehicle.getCurrentPosition()).getPsbTrack();
      return binTrack == vehicleTrack;
  }
  
  private List<DestinationCreationTO> inBoundDestinations(Bin bin, InboundOrder inOrder) {
    String inboundStation = getInBoundStation(bin.getPsbTrack());
    String binStack = inOrder.getAssignedBinStack().getName();
    List<DestinationCreationTO> result = new ArrayList<>();
    // ���վץ��
    result.add(new DestinationCreationTO(inboundStation, OrderBinConstants.OPERATION_LOAD));
    // ָ����λվ����
    result.add(new DestinationCreationTO(binStack, OrderBinConstants.OPERATION_UNLOAD));
    return result;
  }
  
  /**
   * Ϊָ���������ɳ�������ָ���
   * @param bin �����������
   * @return ��������ָ�
   */
  private List<DestinationCreationTO> outBoundDestinations(Bin bin){
    String locationName = bin.getAttachedLocation().getName();
    int psbTrack = bin.getPsbTrack();
    int pstTrack = bin.getPstTrack();
    int binPosition = bin.getBinPosition();
    
    List<DestinationCreationTO> result = new ArrayList<>();
    int stackSize = getStackSize(locationName);
    List<String> tmpLocs = getVacantNeighbours(psbTrack, pstTrack, stackSize - 1 - binPosition);
    if(tmpLocs == null)
      return null;
    // �ù���ĳ���վ
    String outboundStation = getOutBoundStation(psbTrack);

    // ����
    for(String tmpLoc:tmpLocs){
      result.add(new DestinationCreationTO(locationName, OrderBinConstants.OPERATION_LOAD));
      result.add(new DestinationCreationTO(tmpLoc, OrderBinConstants.OPERATION_UNLOAD));
    }
    tmpLocs.size();
   
    // ָ���������
    result.add(new DestinationCreationTO(locationName, OrderBinConstants.OPERATION_LOAD));
    result.add(new DestinationCreationTO(outboundStation, OrderBinConstants.OPERATION_UNLOAD));

    return result;
  }
  
  /**
   * ������ĳ��������Ҫ����ʱ��Ϊ��Ѱ�Ҹ����ܹ����ڵ���Ŀ�λ.
   * @param psbTrack ��Ҫ������������ڵ�PSB���.
   * @param pstTrack ��Ҫ������������ڵ�PST���.
   * @param vacancyNum ��Ҫ���ڵ���Ŀ�λ����
   * @return ���ڵ���Ŀ�λ.
   */
  private List<String> getVacantNeighbours(int psbTrack, int pstTrack, int vacancyNum){
    int offset = 1;
    int row = psbTrack - 1;
    int column = pstTrack - 1;
    List<String> vacantNeighbours = new ArrayList<>();
      while(vacancyNum > 0 
          && (column-offset >= 0 || column+offset < locationPosition[row].length)){
        // A vacant neighbour firstly should be valid.
        // A picking station is not considered as a vacant neighbour.
        if(column-offset >= 0
            && locationPosition[row][column-offset] != null
            && !isOutBoundStation(locationPosition[row][column-offset])){
          int vacancy = Location.BINS_MAX_NUM - getStackSize(locationPosition[row][column-offset]);
          vacancy = vacancy > vacancyNum ? vacancyNum : vacancy;
          for(int i=0;i<vacancy;i++)
            vacantNeighbours.add(locationPosition[row][column-offset]);
          vacancyNum -= vacancy;
        }
        
        if(vacancyNum > 0 
            && column+offset < locationPosition[row].length
            && locationPosition[row][column+offset] != null
            && !isOutBoundStation(locationPosition[row][column+offset])){
          int vacancy = Location.BINS_MAX_NUM - getStackSize(locationPosition[row][column+offset]);
          vacancy = vacancy > vacancyNum ? vacancyNum : vacancy;
          for(int i=0;i<vacancy;i++)
            vacantNeighbours.add(locationPosition[row][column+offset]);
          vacancyNum -= vacancy;
        }
        offset++;
      }
    return vacancyNum <= 0 ? vacantNeighbours : null;
  }
  
  /**
   * ��ѯָ����λվ�ĵ�ǰ���������.
   * @param locationName ָ����λվ��ID
   * @return ��λվ�ĵ�ǰ���������.
   */
  @Override
  public int getStackSize(String locationName){
    if(locationName == null)
      return 0;
    return fetchObject(Location.class, locationName).stackSize();
  }
  
  /**
   * ��ȡָ������ϵ����վID.
   * @param psbTrack ָ�����.
   * @return ָ������ϵ����վID.
   */
  public String getInBoundStation(int psbTrack){
    for(String location:locationPosition[psbTrack-1]){
      if(location != null && isInBoundStation(location))
        return location;
    }
    return null;
  }
  
  private boolean isInBoundStation(String locationName) {
    return fetchObject(Location.class,locationName)
              .getType().getName().equals(Location.IN_BOUND_STATION_TYPE);
  }
  
  /**
   * ��ȡָ������ϵĳ���վID.
   * @param psbTrack ָ�����.
   * @return ָ������ϵĳ���վID.
   */
  private String getOutBoundStation(int psbTrack){
    for(String location:locationPosition[psbTrack-1]){
      if(location != null && isOutBoundStation(location))
        return location;
    }
    return null;
  }
  
  private boolean isOutBoundStation(String locationName) {
    return fetchObject(Location.class,locationName)
              .getType().getName().equals(Location.OUT_BOUND_STATION_TYPE);
  }
  
  @Override
  public void updateTrackInfo(){
    synchronized(globalSyncObject){
      List<Long> sortedXPos = new ArrayList<>();
      List<Long> sortedYPos = new ArrayList<>();
      for (Point point:fetchObjects(Point.class)){
        sortedXPos.add(point.getPosition().getX());
        sortedYPos.add(point.getPosition().getY());
      }
      List<Long> psbTrackPool;
      List<Long> pstTrackPool;
      switch(TrackDefinition.PSB_TRACK_DEFINITION){
        case Y_POSITION :
          // ���PSB�������Y��������л���
          psbTrackPool = sortedYPos.stream().distinct().sorted().collect(Collectors.toList());
          pstTrackPool = sortedXPos.stream().distinct().sorted().collect(Collectors.toList());

          locationPosition = new String[psbTrackPool.size()][pstTrackPool.size()];

          for (Point point:fetchObjects(Point.class)){
            point.setPsbTrack(psbTrackPool.indexOf(point.getPosition().getY()) + 1);
            point.setPstTrack(pstTrackPool.indexOf(point.getPosition().getX()) + 1);
            objectPool.replaceObject(point);
            Location tmpLoc;
            for(Location.Link link:point.getAttachedLinks()){
              tmpLoc = fetchObject(Location.class, link.getLocation());
              tmpLoc.setPsbTrack(point.getPsbTrack());
              tmpLoc.setPstTrack(point.getPstTrack());

              for(Bin bin : tmpLoc.getBins())
                objectPool.replaceObject(bin);

              objectPool.replaceObject(tmpLoc);
              locationPosition[tmpLoc.getPsbTrack()-1][tmpLoc.getPstTrack()-1] = tmpLoc.getName();
            }
          }
          break;
        default :
          // ���PSB�������X��������л���
          psbTrackPool = sortedXPos.stream().distinct().sorted().collect(Collectors.toList());
          pstTrackPool = sortedYPos.stream().distinct().sorted().collect(Collectors.toList());

          locationPosition = new String[psbTrackPool.size()][pstTrackPool.size()];

          for (Point point:fetchObjects(Point.class)){
            point.setPsbTrack(psbTrackPool.indexOf(point.getPosition().getX()) + 1);
            point.setPstTrack(pstTrackPool.indexOf(point.getPosition().getY()) + 1);
            objectPool.replaceObject(point);
            Location tmpLoc;
            for(Location.Link link:point.getAttachedLinks()){
              tmpLoc = fetchObject(Location.class, link.getLocation());
              tmpLoc.setPsbTrack(point.getPsbTrack());
              tmpLoc.setPstTrack(point.getPstTrack());

              for(Bin bin : tmpLoc.getBins())
                objectPool.replaceObject(bin);

              objectPool.replaceObject(tmpLoc);
              locationPosition[tmpLoc.getPsbTrack()-1][tmpLoc.getPstTrack()-1] = tmpLoc.getName();
            }
          }
          break;
      }
    }
  }

  @Override
  public String[][] getLocPosition() {
    return locationPosition;
  }
  
  private boolean couldProcessTransportOrder(Vehicle vehicle) {
    return vehicle.getIntegrationLevel() == Vehicle.IntegrationLevel.TO_BE_UTILIZED
        && vehicle.getType().equals(Vehicle.BIN_VEHICLE_TYPE)
        && vehicle.getCurrentPosition() != null
        && !vehicle.isEnergyLevelCritical()
        && (processesNoOrder(vehicle)
            || processesDispensableOrder(vehicle));
  }
  
  private boolean processesNoOrder(Vehicle vehicle) {
    return vehicle.hasProcState(Vehicle.ProcState.IDLE)
        && (vehicle.hasState(Vehicle.State.IDLE)
            || vehicle.hasState(Vehicle.State.CHARGING));
  }

  private boolean processesDispensableOrder(Vehicle vehicle) {
    return vehicle.hasProcState(Vehicle.ProcState.PROCESSING_ORDER)
        && fetchObject(TransportOrder.class, vehicle.getTransportOrder())
            .isDispensable();
  }

  private int compareDistance(Vehicle psb1, Vehicle psb2, int binTrack) {
    Integer distance1 = Math.abs(fetchObject(Point.class,
                                             psb1.getCurrentPosition()).getPsbTrack() - binTrack);
    Integer distance2 = Math.abs(fetchObject(Point.class,
                                             psb2.getCurrentPosition()).getPsbTrack() - binTrack);
    return  distance1.compareTo(distance2);
  }

  private Bin createBin(InboundOrder order, int binTrack) {
    synchronized(globalSyncObject){
      Location inStation = fetchObject(Location.class, getInBoundStation(binTrack));
      Bin newBin = order.getBin().withAttachedLocation(inStation.getReference())
          .withPsbTrack(binTrack)
          .withPstTrack(inStation.getPstTrack())
          .withBinPosition(inStation.stackSize())
          .withState(Bin.State.Still);
      objectPool.addObject(newBin);
      objectPool.emitObjectEvent(newBin.clone(), null, TCSObjectEvent.Type.OBJECT_CREATED);

      Location previous = inStation;
      inStation.push(newBin);
      objectPool.replaceObject(inStation);
      objectPool.emitObjectEvent(inStation, previous, TCSObjectEvent.Type.OBJECT_MODIFIED);

      inOrderService.setInboundOrderBin(order.getReference(), newBin);
      return newBin;
    }
  }
}