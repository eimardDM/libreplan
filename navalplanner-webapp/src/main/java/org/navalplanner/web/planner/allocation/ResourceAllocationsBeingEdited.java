/*
 * This file is part of ###PROJECT_NAME###
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.planner.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.joda.time.LocalDate;
import org.navalplanner.business.planner.entities.AggregateOfResourceAllocations;
import org.navalplanner.business.planner.entities.CalculatedValue;
import org.navalplanner.business.planner.entities.ResourceAllocation;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.business.planner.entities.allocationalgorithms.AllocationModification;
import org.navalplanner.business.planner.entities.allocationalgorithms.ResourcesPerDayModification;
import org.navalplanner.business.resources.daos.IResourceDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.Resource;

public class ResourceAllocationsBeingEdited {

    public static AllocationResult createInitialAllocation(Task task) {
        Set<ResourceAllocation<?>> resourceAllocations = task
                .getResourceAllocations();
        Map<ResourcesPerDayModification, ResourceAllocation<?>> forModification = forModification(resourceAllocations);
        AggregateOfResourceAllocations aggregate = new AggregateOfResourceAllocations(
                AllocationModification.getBeingModified(forModification
                        .keySet()));
        return AllocationResult.create(task, task.getCalculatedValue(), aggregate, forModification);
    }

    private static Map<ResourcesPerDayModification, ResourceAllocation<?>> forModification(
            Collection<ResourceAllocation<?>> resourceAllocations) {
        Map<ResourcesPerDayModification, ResourceAllocation<?>> result = new HashMap<ResourcesPerDayModification, ResourceAllocation<?>>();
        for (ResourceAllocation<?> resourceAllocation : resourceAllocations) {
            result.put(resourceAllocation.copy().asResourcesPerDayModification(),
                    resourceAllocation);
        }
        return result;
    }

    public static ResourceAllocationsBeingEdited create(Task task,
            List<AllocationRow> initialAllocations, IResourceDAO resourceDAO) {
        return new ResourceAllocationsBeingEdited(task, initialAllocations);
    }

    private final List<AllocationRow> currentRows;

    private final Set<ResourceAllocation<?>> requestedToRemove = new HashSet<ResourceAllocation<?>>();

    private final Task task;

    private FormBinder formBinder = null;

    private CalculatedValue calculatedValue;

    private Integer daysDuration;

    private ResourceAllocationsBeingEdited(Task task,
            List<AllocationRow> initialRows) {
        this.task = task;
        this.currentRows = new ArrayList<AllocationRow>(initialRows);
        this.calculatedValue = task.getCalculatedValue();
        this.daysDuration = task.getDaysDuration();
    }

    public void addSpecificResourceAllocationFor(List<Resource> resource) {
        List<Resource> alreadyPresent = new ArrayList<Resource>();
        for (Resource each : resource) {
            if (alreadyExistsAllocationFor(each)) {
                alreadyPresent.add(each);
            } else {
                currentRows.add(SpecificAllocationRow.forResource(each));
                formBinder.newAllocationAdded();
            }
        }
        if (!alreadyPresent.isEmpty()) {
            formBinder.markRepeatedResources(alreadyPresent);
        }
    }

    public void addGeneric(Set<Criterion> criterions,
            Collection<? extends Resource> resourcesMatched) {
        if (resourcesMatched.isEmpty()) {
            formBinder.markNoWorkersMatchedByCriterions(criterions);
        } else {
            GenericAllocationRow genericAllocationRow = GenericAllocationRow
                    .create(criterions, resourcesMatched);
            if (alreadyExistsAllocationFor(criterions)) {
                formBinder.markThereisAlreadyAssignmentWith(criterions);
            } else {
                currentRows.add(genericAllocationRow);
                formBinder.newAllocationAdded();
            }
        }
    }

    public List<AllocationRow> getCurrentRows() {
        return new ArrayList<AllocationRow>(currentRows);
    }

    private boolean alreadyExistsAllocationFor(Resource resource) {
        return !getAllocationsFor(resource).isEmpty();
    }

    private boolean alreadyExistsAllocationFor(Set<Criterion> criterions) {
        List<GenericAllocationRow> generic = AllocationRow
                .getGeneric(getCurrentRows());
        for (GenericAllocationRow each : generic) {
            if (each.hasSameCriterions(criterions)) {
                return true;
            }
        }
        return false;
    }

    private List<SpecificAllocationRow> getAllocationsFor(Resource resource) {
        List<SpecificAllocationRow> found = SpecificAllocationRow.withResource(
                SpecificAllocationRow.getSpecific(currentRows), resource);
        return found;
    }

    public void remove(AllocationRow row) {
        currentRows.remove(row);
        if (row.isModifying()) {
            requestedToRemove.add(row.getOrigin());
        }
    }

    public Set<ResourceAllocation<?>> getAllocationsRequestedToRemove() {
        return requestedToRemove;
    }

    private Map<AllocationRow, ResourceAllocation<?>> allocationsWithTheirRelatedAllocationsOnTask() {
        Map<AllocationRow, ResourceAllocation<?>> result = new HashMap<AllocationRow, ResourceAllocation<?>>();
        for (AllocationRow row : withoutZeroResourcesPerDayAllocations(currentRows)) {
            result.put(row, row.getOrigin());
        }
        return result;
    }


    private List<AllocationRow> withoutZeroResourcesPerDayAllocations(
            List<AllocationRow> rows) {
        List<AllocationRow> result = new ArrayList<AllocationRow>();
        for (AllocationRow each : rows) {
            if (!each.isEmptyResourcesPerDay()) {
                result.add(each);
            }
        }
        return result;
    }

    public void checkInvalidValues() {
        if (formBinder.getCalculatedValue() != CalculatedValue.NUMBER_OF_HOURS
                && formBinder.getAssignedHours() <= 0) {
            formBinder.markAssignedHoursMustBePositive();
        }
        if (formBinder.getAllocationEnd().isBefore(
                new LocalDate(task.getStartDate()))) {
            formBinder.markEndDateMustBeAfterStartDate();
        }
    }

    public AllocationResult doAllocation() {
        checkInvalidValues();
        Map<ResourcesPerDayModification, ResourceAllocation<?>> fromDetachedToAttached = getAllocationsWithRelationshipsToOriginal();
        List<ResourcesPerDayModification> allocations = asList(fromDetachedToAttached);
        if (!allocations.isEmpty()) {
            switch (calculatedValue) {
            case NUMBER_OF_HOURS:
                ResourceAllocation.allocating(allocations).allocateUntil(
                        formBinder.getAllocationEnd());
                break;
            case END_DATE:
                ResourceAllocation.allocating(allocations).untilAllocating(
                        formBinder.getAssignedHours());
                break;
            default:
                throw new RuntimeException("cant handle: " + calculatedValue);
            }
        }
        AllocationResult result = AllocationResult.create(task, calculatedValue, new AggregateOfResourceAllocations(AllocationModification
                .getBeingModified(allocations)), fromDetachedToAttached);
        daysDuration = result.getDaysDuration();
        return result;
    }

    private Map<ResourcesPerDayModification, ResourceAllocation<?>> getAllocationsWithRelationshipsToOriginal() {
        Map<AllocationRow, ResourceAllocation<?>> allocationsWithTheirRelatedAllocationsOnTask = allocationsWithTheirRelatedAllocationsOnTask();
        Map<ResourcesPerDayModification, ResourceAllocation<?>> fromDetachedToAttached = instantiate(allocationsWithTheirRelatedAllocationsOnTask);
        return fromDetachedToAttached;
    }

    private List<ResourcesPerDayModification> asList(
            Map<ResourcesPerDayModification, ResourceAllocation<?>> map) {
        return new ArrayList<ResourcesPerDayModification>(
                map.keySet());
    }

    private Map<ResourcesPerDayModification, ResourceAllocation<?>> instantiate(
            Map<AllocationRow, ResourceAllocation<?>> allocationsWithTheirRelatedAllocationsOnTask) {
        Map<ResourcesPerDayModification, ResourceAllocation<?>> result = new HashMap<ResourcesPerDayModification, ResourceAllocation<?>>();
        for (Entry<AllocationRow, ResourceAllocation<?>> entry : allocationsWithTheirRelatedAllocationsOnTask
                .entrySet()) {
            AllocationRow key = entry.getKey();
            ResourcesPerDayModification instantiated = instantiate(key);
            result.put(instantiated, entry.getValue());
            key.setLast(instantiated.getBeingModified());
        }
        return result;
    }

    private ResourcesPerDayModification instantiate(AllocationRow row) {
        return row.toResourcesPerDayModification(task);
    }

    public FormBinder createFormBinder(
            IResourceAllocationModel resourceAllocationModel) {
        if (formBinder != null) {
            throw new IllegalStateException(
                    "there is already a binder associated with this object");
        }
        formBinder = new FormBinder(this, resourceAllocationModel);
        return formBinder;
    }

    public CalculatedValue getCalculatedValue() {
        return this.calculatedValue;
    }

    public void setCalculatedValue(CalculatedValue calculatedValue) {
        this.calculatedValue = calculatedValue;
        this.daysDuration = task.getDaysDuration();
    }

    public AllocationResult getInitialAllocation() {
        return createInitialAllocation(task);
    }


    public Task getTask() {
        return task;
    }

    public Integer getDaysDuration() {
        return daysDuration;
    }

    public Set<Resource> getAllocationResources() {
        Set<Resource> result = new HashSet<Resource>();
        for (AllocationRow each : currentRows) {
            result.addAll(each.getAssociatedResources());
        }
        return result;
    }

    public Date getEnd() {
        LocalDate start = new LocalDate(getStartDate());
        return toDate(start.plusDays(getDaysDuration()));
    }

    public Date getStartDate() {
        return task.getStartDate();
    }

    private Date toDate(LocalDate date) {
        return date.toDateTimeAtStartOfDay().toDate();
    }

}